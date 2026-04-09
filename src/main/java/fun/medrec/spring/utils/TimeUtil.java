package fun.medrec.spring.utils;

public final class TimeUtil {
    public static void measureTime(int iterations, Runnable task) {
        // 正式测量
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            long eachStart = System.nanoTime();
            task.run();
            long eachEnd = System.nanoTime();
            long eachTime = eachEnd - eachStart;
            System.out.println("第 " + (i + 1) + " 次循环耗时: " + eachTime / 1_000_000 + " ms");

        }
        long endTime = System.nanoTime();

        long totalTime = endTime - startTime;
        double avgTime = totalTime / (double) iterations;

        System.out.println("循环次数: " + iterations);
        System.out.println("总耗时: " + totalTime / 1_000_000 + " ms");
        System.out.println("平均耗时: " + avgTime / 1_000_000 + " ms");
    }
}
