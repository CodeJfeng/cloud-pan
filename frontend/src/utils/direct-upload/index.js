import panUtil from '@/utils/common'
import fileService from '@/api/file'

const DIRECT_UPLOAD_THRESHOLD = 10 * 1024 * 1024

const UPLOAD_CACHE_KEY_PREFIX = 'direct_upload_cache_'

class UploadTask {
    constructor(md5) {
        this.md5 = md5
        this.isPaused = false
        this.isCancelled = false
        this.abortController = null
    }

    pause() {
        this.isPaused = true
        if (this.abortController) {
            this.abortController.abort()
        }
    }

    resume() {
        this.isPaused = false
    }

    cancel() {
        this.isPaused = true
        this.isCancelled = true
        if (this.abortController) {
            this.abortController.abort()
        }
    }
}

class DirectUploader {
    constructor() {
        this.maxConcurrentUploads = 3
        this.uploadingCount = 0
        this.pendingChunks = []
        this.uploadTasks = new Map()
    }

    getTask(md5) {
        if (!this.uploadTasks.has(md5)) {
            this.uploadTasks.set(md5, new UploadTask(md5))
        }
        return this.uploadTasks.get(md5)
    }

    removeTask(md5) {
        this.uploadTasks.delete(md5)
    }

    async upload(file, md5, parentId, onProgress) {
        const fileSize = file.size
        const chunkSize = panUtil.getChunkSize()
        const task = this.getTask(md5)

        if (fileSize <= DIRECT_UPLOAD_THRESHOLD) {
            return await this.uploadSmallFile(task, file, md5, parentId, onProgress)
        } else {
            return await this.uploadLargeFile(task, file, md5, parentId, chunkSize, onProgress)
        }
    }

    async uploadSmallFile(task, file, md5, parentId, onProgress) {
        const res = await new Promise((resolve, reject) => {
            fileService.generatePresignedUrl({
                filename: file.name,
                totalSize: file.size,
                contentType: file.type || 'application/octet-stream'
            }, resolve, reject)
        })

        if (res.code !== 0) {
            throw new Error('获取预签名URL失败: ' + res.msg)
        }

        const { uploadUrl, objectKey } = res.data

        await this.uploadToS3(task, uploadUrl, file, onProgress)

        await new Promise((resolve, reject) => {
            fileService.completeDirectUpload({
                objectKey: objectKey,
                filename: file.name,
                totalSize: file.size,
                identifier: md5,
                parentId: parentId
            }, resolve, reject)
        })

        this.removeTask(md5)
        return { success: true, objectKey }
    }

    async uploadLargeFile(task, file, md5, parentId, chunkSize, onProgress) {
        const totalChunks = Math.ceil(file.size / chunkSize)

        const cacheKey = UPLOAD_CACHE_KEY_PREFIX + md5
        const cachedUpload = this.getCachedUpload(cacheKey)

        let objectKey, uploadId

        if (cachedUpload && cachedUpload.objectKey && cachedUpload.uploadId) {
            objectKey = cachedUpload.objectKey
            uploadId = cachedUpload.uploadId

            const uploadedPartsRes = await new Promise((resolve, reject) => {
                fileService.listUploadedParts({
                    objectKey: objectKey,
                    uploadId: uploadId
                }, resolve, reject)
            })

            if (uploadedPartsRes.code === 0 && uploadedPartsRes.data) {
                const uploadedParts = uploadedPartsRes.data.uploadedParts || []
                console.log(`断点续传：已上传分片 ${uploadedParts.join(', ')}`)

                const parts = cachedUpload.parts || []
                for (let i = 0; i < totalChunks; i++) {
                    await this.checkPauseState(task)

                    const partNumber = i + 1
                    if (uploadedParts.includes(partNumber)) {
                        console.log(`跳过已上传分片: ${partNumber}`)
                        if (!parts[i]) {
                            parts[i] = {
                                partNumber: partNumber,
                                eTag: ''
                            }
                        }
                        continue
                    }

                    const partResult = await this.uploadSinglePart(task, file, i, chunkSize, objectKey, uploadId, partNumber, onProgress)
                    parts[i] = partResult
                    this.saveCachedUpload(cacheKey, { objectKey, uploadId, parts: parts })
                }

                await this.completeUpload(objectKey, uploadId, file.name, file.size, md5, parentId, parts)
                this.clearCachedUpload(cacheKey)
                this.removeTask(md5)
                return { success: true, objectKey, uploadId }
            }
        }

        const initRes = await new Promise((resolve, reject) => {
            fileService.initMultipartUpload({
                filename: file.name,
                totalSize: file.size,
                totalChunks: totalChunks,
                contentType: file.type || 'application/octet-stream'
            }, resolve, reject)
        })

        if (initRes.code !== 0) {
            throw new Error('初始化分片上传失败: ' + initRes.msg)
        }

        objectKey = initRes.data.objectKey
        uploadId = initRes.data.uploadId

        this.saveCachedUpload(cacheKey, { objectKey, uploadId, parts: [] })

        const parts = []
        for (let i = 0; i < totalChunks; i++) {
            await this.checkPauseState(task)
            const partResult = await this.uploadSinglePart(task, file, i, chunkSize, objectKey, uploadId, i + 1, onProgress)
            parts.push(partResult)
            this.saveCachedUpload(cacheKey, { objectKey, uploadId, parts: [...parts] })
        }

        await this.completeUpload(objectKey, uploadId, file.name, file.size, md5, parentId, parts)
        this.clearCachedUpload(cacheKey)
        this.removeTask(md5)
        return { success: true, objectKey, uploadId }
    }

    async uploadSinglePart(task, file, chunkIndex, chunkSize, objectKey, uploadId, partNumber, onProgress) {
        const start = chunkIndex * chunkSize
        const end = Math.min(start + chunkSize, file.size)
        const chunk = file.slice(start, end)

        const partRes = await new Promise((resolve, reject) => {
            fileService.generatePresignedPartUrl({
                objectKey: objectKey,
                uploadId: uploadId,
                partNumber: partNumber,
                partSize: chunk.size
            }, resolve, reject)
        })

        if (partRes.code !== 0) {
            throw new Error(`获取分片${partNumber}预签名URL失败: ` + partRes.msg)
        }

        const partUploadUrl = partRes.data.uploadUrl

        const response = await this.uploadToS3(task, partUploadUrl, chunk, (loaded, total) => {
            if (onProgress) {
                const uploadedSize = (chunkIndex * chunkSize) + loaded
                const totalSize = file.size
                const percentage = Math.floor((uploadedSize / totalSize) * 100)
                onProgress(uploadedSize, totalSize, percentage)
            }
        })

        const eTag = response.eTag
        if (!eTag) {
            throw new Error(`分片${partNumber}上传失败，未返回ETag`)
        }

        const cleanETag = eTag.replace(/"/g, '')
        return {
            partNumber: partNumber,
            eTag: cleanETag
        }
    }

    async completeUpload(objectKey, uploadId, filename, totalSize, md5, parentId, parts) {
        await new Promise((resolve, reject) => {
            fileService.completeDirectUpload({
                objectKey: objectKey,
                uploadId: uploadId,
                filename: filename,
                totalSize: totalSize,
                identifier: md5,
                parentId: parentId,
                parts: parts
            }, resolve, reject)
        })
    }

    uploadToS3(task, url, data, onProgress) {
        return new Promise((resolve, reject) => {
            task.abortController = new AbortController()
            const xhr = new XMLHttpRequest()

            xhr.open('PUT', url, true)

            xhr.upload.onprogress = (event) => {
                if (event.lengthComputable && onProgress) {
                    onProgress(event.loaded, event.total)
                }
            }

            xhr.onload = () => {
                if (xhr.status >= 200 && xhr.status < 300) {
                    const eTag = xhr.getResponseHeader('ETag')
                    const allHeaders = xhr.getAllResponseHeaders()
                    console.log('S3 Response Headers:', allHeaders)
                    console.log('ETag:', eTag)
                    resolve({ status: xhr.status, eTag: eTag })
                } else {
                    reject(new Error(`上传失败，状态码: ${xhr.status}, 响应: ${xhr.responseText}`))
                }
            }

            xhr.onerror = () => {
                reject(new Error('网络错误，上传失败'))
            }

            xhr.onabort = () => {
                if (task.isCancelled) {
                    reject(new Error('上传已取消'))
                } else {
                    reject(new Error('上传已暂停'))
                }
            }

            task.abortController.signal.addEventListener('abort', () => {
                xhr.abort()
            })

            xhr.send(data)
        })
    }

    async checkPauseState(task) {
        if (task.isCancelled) {
            throw new Error('上传已取消')
        }
        if (task.isPaused) {
            throw new Error('上传已暂停')
        }
    }

    getCachedUpload(cacheKey) {
        try {
            const cached = localStorage.getItem(cacheKey)
            if (cached) {
                return JSON.parse(cached)
            }
        } catch (e) {
            console.warn('读取上传缓存失败:', e)
        }
        return null
    }

    saveCachedUpload(cacheKey, data) {
        try {
            localStorage.setItem(cacheKey, JSON.stringify(data))
        } catch (e) {
            console.warn('保存上传缓存失败:', e)
        }
    }

    clearCachedUpload(cacheKey) {
        try {
            localStorage.removeItem(cacheKey)
        } catch (e) {
            console.warn('清除上传缓存失败:', e)
        }
    }
}

export default new DirectUploader()
