/*
 * Copyright (C) 2014 Square, Inc.
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

import java.io.IOException;

/**
 * A call is a request that has been prepared for execution. A call can be canceled. As this object
 * represents a single request/response pair (stream), it cannot be executed twice.
 */
public interface Call extends Cloneable {
  /** Returns the original request that initiated this call. */
  //返回原始的请求对象。
  Request request();

  /**
   * Invokes the request immediately, and blocks until the response can be processed or is in
   * error.
   *
   * <p>To avoid leaking resources callers should close the {@link Response} which in turn will
   * close the underlying {@link ResponseBody}.
   *
   * <pre>@{code
   *
   *   // ensure the response (and underlying response body) is closed
   *   try (Response response = client.newCall(request).execute()) {
   *     ...
   *   }
   *
   * }</pre>
   *
   * <p>The caller may read the response body with the response's {@link Response#body} method. To
   * avoid leaking resources callers must {@linkplain ResponseBody close the response body} or the
   * Response.
   *
   * <p>Note that transport-layer success (receiving a HTTP response code, headers and body) does
   * not necessarily indicate application-layer success: {@code response} may still indicate an
   * unhappy HTTP response code like 404 or 500.
   *
   * @throws IOException if the request could not be executed due to cancellation, a connectivity
   * problem or timeout. Because networks can fail during an exchange, it is possible that the
   * remote server accepted the request before the failure.
   * @throws IllegalStateException when the call has already been executed.
   * 同步方法，需要新开的线程里面去处理，都是应该在子线程中去做，而返回了Respond
   */
  //同步发起请求。
  Response execute() throws IOException;

  /**
   * Schedules the request to be executed at some point in the future.
   *
   * <p>The {@link OkHttpClient#dispatcher dispatcher} defines when the request will run: usually
   * immediately unless there are several other requests currently being executed.
   *
   * <p>This client will later call back {@code responseCallback} with either an HTTP response or a
   * failure exception.
   *
   * @throws IllegalStateException when the call has already been executed.
   * 异步请求，可以直接在主线程运行，回调的onRespond和onFailure同时也应该运行在子线程中。
   * 得到的值在Callback中
   */
  //异步发起请求。
  void enqueue(Callback responseCallback);

  /** Cancels the request, if possible. Requests that are already complete cannot be canceled. */
  //取消请求。
  void cancel();

  /**
   * Returns true if this call has been either {@linkplain #execute() executed} or {@linkplain
   * #enqueue(Callback) enqueued}. It is an error to execute a call more than once.
   */
  //请求是否已经被执行。
  boolean isExecuted();
  //请求是否取消。
  boolean isCanceled();

  /**
   * Create a new, identical call to this one which can be enqueued or executed even if this call
   * has already been.
   */
  //clone 对象。
  Call clone();
  //工厂类。
  interface Factory {
    Call newCall(Request request);
  }
}
