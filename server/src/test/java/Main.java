import java.util.Arrays;
import java.util.PriorityQueue;
import java.util.Scanner;

// 注意类名必须为 Main, 不要有任何 package xxx 信息
public class Main {
  public static void main(String[] args) {
    Scanner sc = new Scanner(System.in);
    int n = sc.nextInt();
    int m = sc.nextInt();
    int[] friend = new int[n];
    int[][] horse = new int[m][2];
    for (int i = 0; i < n; i++) {
      friend[i] = sc.nextInt();
    }
    for (int i = 0; i < m; i++) {
      horse[i][0] = sc.nextInt(); // 舒适度
      horse[i][1] = sc.nextInt(); // 价格
    }

    // 贪心算法
    Arrays.sort(friend);
    Arrays.sort(horse, (a, b) -> a[1] - b[1]);
    // 判断每一个好友买到的房子价值 要求舒适度尽可能大
    PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> b - a);
    long ans = 0l;
    int index = 0;
    for (int i = 0; i < n; i++) {
      while (index < m && friend[i] >= horse[index][1]) {
        pq.add(horse[index][0]);
        index++;
      }
      if (!pq.isEmpty()) {
        ans += pq.poll();
      }
    }
    System.out.println(ans);
  }
}