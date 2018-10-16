package com.hibiup;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class FutureTests {
    String testMethod(String name) {
        try {
            Thread.sleep(5000);
            System.out.println("Thread: " + Thread.currentThread());
        } catch (InterruptedException e) {
            throw new RuntimeException("Thread can't sleep!");
        }
        return "Hello, " + name +"!";
    }

    public Executor executorService() {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        return executor;
    }

    @Test
    public void TestCompleteFuture() {
        /** 1) 用 completedFuture 方法执行　testMethod　函数．(不会产生新的线程) */
        CompletableFuture cf = CompletableFuture.completedFuture(testMethod("Future"));
        /** 2) 确认结束　*/
        assertTrue(cf.isDone());
        /** 3) 得到返回值 */
        assertEquals("Hello, Future!", cf.getNow(null));
    }

    @Test
    public void TestRunAsync() {
        /** 1) runAsync 会使用新线程来异步执行，但是不会返回结果．第二个参数可以指定线程池．　*/
        CompletableFuture cf = CompletableFuture.runAsync(() -> {
            /** 2) 不返回结果 */
            testMethod("Future");
        }, executorService());

        /** 3) join 方法等待执行结束　*/
        cf.join();
        /** 4) 确认结束　*/
        assertTrue(cf.isDone());
        /** 5) 不能得到返回值 */
        assertEquals(null, cf.getNow(null));
    }

    @Test
    public void TestSupplyAsync() {
        /** 1) supplyAsync 会使用新线程来异步执行，并且可以得到返回值．　*/
        CompletableFuture cf = CompletableFuture.supplyAsync(() -> {
            /** 2) 可以返回结果．*/
            return testMethod("Future");
        }, executorService());

        cf.join();
        assertTrue(cf.isDone());
        assertEquals("Hello, Future!", cf.getNow(null));
    }


    @Test
    public void TestThenApplyAsync() {
        /** 1) supplyAsync 会使用新线程来异步执行，并且可以得到返回值．　*/
        CompletableFuture cf = CompletableFuture.supplyAsync(() -> {
            /** 2) 可以返回结果．*/
            return testMethod("Future");
        }, executorService())
                /** 3) 可以继续添加后续步骤，也可以指定线程池　*/
                .thenApplyAsync(s -> s.toUpperCase(), executorService());

        cf.join();
        assertTrue(cf.isDone());
        /** 4) 得到结果 */
        assertEquals("HELLO, FUTURE!", cf.getNow(null));
    }

    @Test
    public void TestResultHandler() {
        /** 1) supplyAsync 会使用新线程来异步执行，并且可以得到返回值．　*/
        CompletableFuture cf = CompletableFuture.supplyAsync(() -> {
            /** 2) 假设存在异常．*/
            throw new RuntimeException("Opoos...");
            //return testMethod("Future");
        }, executorService());

        /** 3) 如果处理可能存在的异常，可以采用 handle 来捕获，并转成可接受的结果。
         *     s: 如果没有异常，将得到返回值
         *     t: 否则得到 Throwable,　这个 Throwable 包含了之前的异常。　*/
        CompletableFuture resultHandler = cf.handle((s, t) -> {
            return (t != null) ? "Job throws error!" : s;
        });

        /** 4) 通过　resultHandler.join 来获取可接受的结果。*/
        Object res = resultHandler.join();
        assertEquals("Job throws error!", res.toString());

        /** 4-1) 或通过依然通过 join 捕获异常。*/
        try {
            cf.join();
        }
        catch(CompletionException ex) {
            assertEquals("Opoos...", ex.getCause().getMessage());
        }
    }

    @Test
    public void TestConcurrencyResultHandler() {
        List<String> names = new ArrayList();
        names.add("Earth");
        names.add("Solar");

        List<CompletableFuture> futures = names.stream().map(name -> {
            /** 1) 假设存在多个并发任务，逐个定义 */
            CompletableFuture stage1 = CompletableFuture.supplyAsync(() -> {
                String s = testMethod(name);  // 会睡眠两秒
                /** 2) 假设部分存在异常．*/
                if (name == "Earth")
                    throw new RuntimeException("Opoos...");
                else
                    return s;
            }, executorService())
                    /** 2-1) 继续后续操作．*/
                    .thenApplyAsync(s -> {
                        assertEquals("Hello, Solar!", s);
                        System.out.println(s);
                        return s;
                    }, executorService());

            /** 3) 为 future 加上 handler(可选)。*/
            CompletableFuture resultHandler = stage1.handle((s, t) -> (t != null) ? "Job throws error!" : s);

            /** 4) 可选返回 resultHandler */
            return resultHandler;
        })
        /** 5) 将生成的 CompletableFuture 保存到 List。 */
        .collect(Collectors.toList());

        /** 6) 并发执行并等待结果。*/
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[futures.size()])).join();
        }
        catch(Throwable t){
            t.printStackTrace();
        }

        /** 7) 取出结果*/
        futures.forEach(f -> System.out.println(f.getNow(null)));
    }
}
