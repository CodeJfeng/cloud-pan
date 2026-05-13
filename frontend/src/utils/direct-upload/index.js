import panUtil from '@/utils/common'
import fileService from '@/api/file'

const DIRECT_UPLOAD_THRESHOLD = 10 * 1024 * 1024

class DirectUploader {
    constructor() {
        this.maxConcurrentUploads = 3
        this.uploadingCount = 0
        this.pendingChunks = []
    }

    async upload(file, md5, parentId, onProgress) {
        const fileSize = file.size
        const chunkSize = panUtil.getChunkSize()

        if (fileSize <= DIRECT_UPLOAD_THRESHOLD) {
            return await this.uploadSmallFile(file, md5, parentId, onProgress)
        } else {
            return await this.uploadLargeFile(file, md5, parentId, chunkSize, onProgress)
        }
    }

    async uploadSmallFile(file, md5, parentId, onProgress) {
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

        await this.uploadToS3(uploadUrl, file, onProgress)

        await new Promise((resolve, reject) => {
            fileService.completeDirectUpload({
                objectKey: objectKey,
                filename: file.name,
                totalSize: file.size,
                identifier: md5,
                parentId: parentId
            }, resolve, reject)
        })

        return { success: true, objectKey }
    }

    async uploadLargeFile(file, md5, parentId, chunkSize, onProgress) {
        const totalChunks = Math.ceil(file.size / chunkSize)

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

        const { uploadUrl: initUrl, objectKey, uploadId } = initRes.data

        const parts = []

        for (let i = 0; i < totalChunks; i++) {
            const start = i * chunkSize
            const end = Math.min(start + chunkSize, file.size)
            const chunk = file.slice(start, end)
            const partNumber = i + 1

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

            const response = await this.uploadToS3(partUploadUrl, chunk, (loaded, total) => {
                if (onProgress) {
                    const uploadedSize = (i * chunkSize) + loaded
                    const totalSize = file.size
                    const percentage = Math.floor((uploadedSize / totalSize) * 100)
                    onProgress(uploadedSize, totalSize, percentage)
                }
            })

            const eTag = response.eTag
            if (!eTag) {
                throw new Error(`分片${partNumber}上传失败，未返回ETag`)
            }

            parts.push({
                partNumber: partNumber,
                eTag: eTag.replace(/"/g, '')
            })
        }

        await new Promise((resolve, reject) => {
            fileService.completeDirectUpload({
                objectKey: objectKey,
                uploadId: uploadId,
                filename: file.name,
                totalSize: file.size,
                identifier: md5,
                parentId: parentId,
                parts: parts
            }, resolve, reject)
        })

        return { success: true, objectKey, uploadId }
    }

    uploadToS3(url, data, onProgress) {
        return new Promise((resolve, reject) => {
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

            xhr.send(data)
        })
    }
}

export default new DirectUploader()
