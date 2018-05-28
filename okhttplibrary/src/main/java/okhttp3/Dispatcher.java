/*
 * Copyright (C) 2013 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3;

import android.support.annotation.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import okhttp3.RealCall.AsyncCall;
import okhttp3.internal.Util;

/**
 * Policy on when async requests are executed.
 *
 * <p>Each dispatcher uses an {@link ExecutorService} to run calls internally. If you supply your
 * own executor, it should be able to run {@linkplain #getMaxRequests the configured maximum} number
 * of calls concurrently.
 */
public final class Dispatcher {
  private int maxRequests = 64;
  private int maxRequestsPerHost = 5;
  private @Nullable Runnable idleCallback;

  /** Executes calls. Created lazily. */
  private @Nullable ExecutorService executorService;

  /** Ready async calls in the order they'll be run. */
  private final Deque<AsyncCall> readyAsyncCalls = new ArrayDeque<>();

  /** Running asynchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<AsyncCall> runningAsyncCalls = new ArrayDeque<>();

  /** Running synchronous calls. Includes canceled calls that haven't finished yet. */
  private final Deque<RealCall> runningSyncCalls = new ArrayDeque<>();

  public Dispatcher(ExecutorService executorService) {
    this.executorService = executorService;
  }

  public Dispatcher() {
  }

  //Dispatcher内部实现了懒加载的无边界限制的线程池
  public synchronized ExecutorService executorService() {
    if (executorService == null) {
      //对应的是CachedThreadPool 线程池大小无界，适用于执行很多的短期异步任务的程序或者是负载较轻的服务器
      //等待队列使用的是SynchonousQueue 特点 它的 每个插入操作都必须等待另一个线程的移除操作，对于线程池而言，
      // 也就是说：在添加任务到等待队列时，必须要有一个空闲线程正在尝试从等待队列获取任务，才有可能添加成功。
      //因此，当一个任务被添加进入线程池时，会有以下两种情况：
      //如果当前有空闲线程正在尝试从等待队列中获取任务，那么这个 任务将会被交给这个空闲线程 进行处理
     // 如果当前没有空闲线程尝试从等待队列中获取任务，那么将会 创建一个新线程来执行任务
     // 由于设置了等待超时时间，某个线程在60s内都无法获取到新的任务将会被销毁。
      /** 几个参数含义
       * 1、0：核心线程数量，保持在线程池中的线程数量(即使已经空闲)，为0代表线程空闲后不会保留，等待一段时间后停止。
       2、Integer.MAX_VALUE:表示线程池可以容纳最大线程数量
       3、TimeUnit.SECOND:当线程池中的线程数量大于核心线程时，空闲的线程就会等待60s才会被终止，如果小于，则会立刻停止。
       4、new SynchronousQueue<Runnable>()：线程等待队列。同步队列，按序排队，先来先服务
       5、Util.threadFactory("OkHttp Dispatcher", false):线程工厂，直接创建一个名为OkHttp Dispatcher的非守护线程


       (1)SynchronousQueue每个插入操作必须等待另一个线程的移除操作，同样任何一个移除操作都等待另一个线程的插入操作。
       因此队列内部其实没有任何一个元素，或者说容量为0，严格说并不是一种容器，由于队列没有容量，因此不能调用peek等操作，
       因此只有移除元素才有元素，显然这是一种快速传递元素的方式，也就是说在这种情况下元素总是以最快的方式从插入者(生产者)传递给移除者(消费者),
       这在多任务队列中最快的处理任务方式。对于高频请求场景，无疑是最合适的。

       (2)在OKHttp中，创建了一个阀值是Integer.MAX_VALUE的线程池，它不保留任何最小线程，随时创建更多的线程数，
       而且如果线程空闲后，只能多活60秒。所以也就说如果收到20个并发请求，线程池会创建20个线程，
       当完成后的60秒后会自动关闭所有20个线程。他这样设计成不设上限的线程，以保证I/O任务中高阻塞低占用的过程，
       不会长时间卡在阻塞上。
       *
       */
      executorService = new ThreadPoolExecutor(0, Integer.MAX_VALUE, 60, TimeUnit.SECONDS,
          new SynchronousQueue<Runnable>(), Util.threadFactory("OkHttp Dispatcher", false));
    }
    return executorService;
  }

  /**
   * Set the maximum number of requests to execute concurrently. Above this requests queue in
   * memory, waiting for the running calls to complete.
   *
   * <p>If more than {@code maxRequests} requests are in flight when this is invoked, those requests
   * will remain in flight.
   */
  public synchronized void setMaxRequests(int maxRequests) {
    if (maxRequests < 1) {
      throw new IllegalArgumentException("max < 1: " + maxRequests);
    }
    this.maxRequests = maxRequests;
    promoteCalls();
  }

  public synchronized int getMaxRequests() {
    return maxRequests;
  }

  /**
   * Set the maximum number of requests for each host to execute concurrently. This limits requests
   * by the URL's host name. Note that concurrent requests to a single IP address may still exceed
   * this limit: multiple hostnames may share an IP address or be routed through the same HTTP
   * proxy.
   *
   * <p>If more than {@code maxRequestsPerHost} requests are in flight when this is invoked, those
   * requests will remain in flight.
   *
   * <p>WebSocket connections to hosts <b>do not</b> count against this limit.
   */
  public synchronized void setMaxRequestsPerHost(int maxRequestsPerHost) {
    if (maxRequestsPerHost < 1) {
      throw new IllegalArgumentException("max < 1: " + maxRequestsPerHost);
    }
    this.maxRequestsPerHost = maxRequestsPerHost;
    promoteCalls();
  }

  public synchronized int getMaxRequestsPerHost() {
    return maxRequestsPerHost;
  }

  /**
   * Set a callback to be invoked each time the dispatcher becomes idle (when the number of running
   * calls returns to zero).
   *
   * <p>Note: The time at which a {@linkplain Call call} is considered idle is different depending
   * on whether it was run {@linkplain Call#enqueue(Callback) asynchronously} or
   * {@linkplain Call#execute() synchronously}. Asynchronous calls become idle after the
   * {@link Callback#onResponse onResponse} or {@link Callback#onFailure onFailure} callback has
   * returned. Synchronous calls become idle once {@link Call#execute() execute()} returns. This
   * means that if you are doing synchronous calls the network layer will not truly be idle until
   * every returned {@link Response} has been closed.
   */
  public synchronized void setIdleCallback(@Nullable Runnable idleCallback) {
    this.idleCallback = idleCallback;
  }

  synchronized void enqueue(AsyncCall call) 
  {
    //先进行判断。
    //如果当前正在请求的数量小于 64，并且对于同一 host 的请求小于 5，才发起请求。
    //当前正在并发的请求不能超过64且同一个地址的访问不能超过5个才能马上请求
    if (runningAsyncCalls.size() < maxRequests && runningCallsForHost(call) < maxRequestsPerHost) 
    {
      //将该任务加入到正在请求的队列当中。
      runningAsyncCalls.add(call);
      //通过线程池执行任务。
      executorService().execute(call);
    } else 
      {
        //否则加入到等待队列当中。
      readyAsyncCalls.add(call);
    }
  }

  /**
   * Cancel all calls currently enqueued or executing. Includes calls executed both {@linkplain
   * Call#execute() synchronously} and {@linkplain Call#enqueue asynchronously}.
   */
  public synchronized void cancelAll() {
    for (AsyncCall call : readyAsyncCalls) {
      call.get().cancel();
    }

    for (AsyncCall call : runningAsyncCalls) {
      call.get().cancel();
    }

    for (RealCall call : runningSyncCalls) {
      call.cancel();
    }
  }

  //异步回调会执行这个代码
  //promoteCalls的作用为：在最开始时，如果不满足执行条件，那么任务将会被加入到等待队列readyAsyncCalls中，
  // 那么当一个任务执行完之后，就需要去等待队列中寻找符合执行条件的任务，并将它加入到任务队列中执行，
  // 之后的逻辑和前面的相同。
  //todo  异步请求的 promoteCalls()负责ready的Call到running的Call的转化
  //okhttp的线程池管理是通过两个阻塞队列（就绪和执行的个数来控制的）
  private void promoteCalls() 
  {
    //todo  明白这里面的代码含义
    //todo  如果运行队列的个数大于最大请求个数，就暂时不处理
    if (runningAsyncCalls.size() >= maxRequests) return; // Already running max capacity.
    //todo 如果就绪的队列中没有任务，更不需要执行了
    if (readyAsyncCalls.isEmpty()) return; // No ready calls to promote.

    //就绪的队列进行迭代
    for (Iterator<AsyncCall> i = readyAsyncCalls.iterator(); i.hasNext(); )
    {
      //迭代出一个任务
      AsyncCall call = i.next();

      //如果对同一个Host的请求小于最大的5个，才执行
      if (runningCallsForHost(call) < maxRequestsPerHost) 
      {
        //就绪队列干掉这个任务
        i.remove();
        //找到了等待队列中符合执行的条件的任务，那么就执行它。
        //加入到运行队列中，为220行代码作判定
        runningAsyncCalls.add(call);
        executorService().execute(call);
      }

      //如果此时的运行队列大于请求数64，返回，不执行
      if (runningAsyncCalls.size() >= maxRequests) return; // Reached max capacity.
      //可以发现 OkHttp 不是在线程池中维护线程的个数，线程是通过 Dispatcher 间接控制，线程池中的请求都是运行中的请求，
      // 这也就是说线程的重用不是线程池控制的，通过源码我们发现线程重用的地方是请求结束的地方 finished(AsyncCall call) ，
      // 而真正的控制是通过 promoteCalls 方法， 根据 maxRequests 和 maxRequestsPerHost 来调整 runningAsyncCalls 
      // 和 readyAsyncCalls，使运行中的异步请求不超过两种最大值，并且如果队列有空闲，将就绪状态的请求归类为运行中
      
    }
  }

  /** Returns the number of running calls that share a host with {@code call}. */
  private int runningCallsForHost(AsyncCall call) {
    int result = 0;
    for (AsyncCall c : runningAsyncCalls) {
      if (c.get().forWebSocket) continue;
      if (c.host().equals(call.host())) result++;
    }
    return result;
  }

  /** Used by {@code Call#execute} to signal it is in-flight. */
  //Dispatcher.executed 仅仅是将 Call 加入到队列当中，而并没有真正执行。
  synchronized void executed(RealCall call) {
    runningSyncCalls.add(call);
  }

  /** Used by {@code AsyncCall#run} to signal completion. */
  void finished(AsyncCall call) {
    finished(runningAsyncCalls, call, true);
  }

  /** Used by {@code Call#execute} to signal completion. */
  void finished(RealCall call) {
    
    finished(runningSyncCalls, call, false);
  }

  //第三个参数是表示异步还是同步的
  private <T> void finished(Deque<T> calls, T call, boolean promoteCalls) {
    int runningCallsCount;
    Runnable idleCallback;
    synchronized (this) 
    {
      //calls.remove(call) 表示从队列中干掉这个东西，返回是成功还是失败。
      if (!calls.remove(call)) throw new AssertionError("Call wasn't in-flight!");
      //异步的，会执行这个代码
      if (promoteCalls) promoteCalls();
      runningCallsCount = runningCallsCount();
      idleCallback = this.idleCallback;
    }
//如果当前已经没有可以执行的任务，那么调用 idleCallback.run() 方法。
    if (runningCallsCount == 0 && idleCallback != null) {
      idleCallback.run();
    }
  }

  /** Returns a snapshot of the calls currently awaiting execution. */
  public synchronized List<Call> queuedCalls() {
    List<Call> result = new ArrayList<>();
    for (AsyncCall asyncCall : readyAsyncCalls) {
      result.add(asyncCall.get());
    }
    return Collections.unmodifiableList(result);
  }

  /** Returns a snapshot of the calls currently being executed. */
  public synchronized List<Call> runningCalls() {
    List<Call> result = new ArrayList<>();
    result.addAll(runningSyncCalls);
    for (AsyncCall asyncCall : runningAsyncCalls) {
      result.add(asyncCall.get());
    }
    return Collections.unmodifiableList(result);
  }

  public synchronized int queuedCallsCount() {
    return readyAsyncCalls.size();
  }

  public synchronized int runningCallsCount() {
    return runningAsyncCalls.size() + runningSyncCalls.size();
  }
}
