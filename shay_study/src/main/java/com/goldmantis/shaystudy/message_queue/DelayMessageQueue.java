package com.goldmantis.shaystudy.message_queue;

/**
 * Author: Shay-Patrick-Cormac
 * Email: fang47881@126.com
 * Ltd: 金螳螂企业（集团）有限公司
 * Date: 2018/5/9 16:54
 * Version: 1.0
 * Description: 延迟消息
 *
 *  消息队列的还有一种玩法就是发送延迟消息，比如说我想控制当前发送的消息在三秒之后处理，
 *  那这样应该怎么写我们的代码呢，毕竟在网络请求的例子里面，我们完全不在乎消息的执行顺序，（网络请求队列不需要执行顺序）
 *  把请求丢进队列之后就就开始等待回调了 
 * 
 */


/**
 *  这个时候我们可以采用链表这个数据结构来取代队列(当然Java里面链表可以作为队列的实例)，按照每个请求或者消息的执行时间进行排序。
 */
public class DelayMessageQueue 
{

    //小技巧，头执行时间设置为-1，尾设置时间为最大，确保插入新的message，会落入这个区间内，并且
    //不需要考虑头尾为Null的情况，这样代码写起来更加简洁
    private Message head = new Message(-1, null);
    private Message tail = new Message(Long.MAX_VALUE, null);
    private DelayMessageQueue mMessageQueue;

    public DelayMessageQueue() 
    {
        //todo !!!需要把默认的头指针和尾指针连接起来
        head.next = tail;
        tail.prev = head;
    }

    private void executeTask()
    {
        new Thread(new Runnable() {
            @Override
            public void run() {
                //用死循环来不停处理消息
                while (true)
                {
             
                    //注意！！ 这里面说的头结点是指你传入的元素第一个，而非初始化的那个head,那个head可以理解为默认的一个东西，可以无视
                    //第一个条件表示链表中还有元素，链表的头并没有指向尾部。第二个条件表示，mmp我也没看懂啊！！
                    if (head.next!=tail && System.currentTimeMillis() >= head.next.execTime)
                    {
                        //执行过程中，拿出头结点，并且从链表的结构中删除
                        Message current = head.next;
                        //把它的下一个节点拿出来，并指向默认的head
                        Message next = current.next;
                        //执行当前的runable
                        current.task.run();
                        //将它拿下,并将下一个节点和默认的头结点互相指引。
                        current.next = null;
                        current.prev = null;
                        head.next = next;
                        next.prev = head;
                    }
                    
                }
            }
        }).start();
    }
    
    //发送及时消息
    private void post(Runnable task)
    {
        //如果是纯post,把消息放在尾部？？
        Message message = new Message(System.currentTimeMillis(), task);
        //获取默认的尾部的前节点
        Message prev = tail.prev;

        //插入的节点和得到尾部上一个节点互相引用
        prev.next = message;
        message.prev = prev;

        //默认的尾部节点和要插入的节点互相引用
        message.next = tail;
        tail.prev = message;
    }
    
    /**
     * 消息采用链表的方式存储，为的是方便插入新的消息，每次插入尾部的时间复杂度为O(1),插入中间的复杂度为O(n),
     * 大家可以想想如果换成数组会是什么复杂度。
     代码中可以用两个Dummy node作为头和尾，这样我们每次插入新消息的时候不需要检查空指针， 如果头为空，
     我们插入Message还需要做 if(head == null){ head = message } else if( tail == null ){head.next = message; tail = message} 这样的检查。
     3.每次发送延迟消息的时候，遍历循环找到第一个时间比当前要插入的消息的时间小。以下面这个图为例子。

     作者：qing的世界
     链接：https://www.jianshu.com/p/9f3b96937253
     來源：简书
     著作权归作者所有。商业转载请联系作者获得授权，非商业转载请注明出处。
     *
     */
    //发送延时消息
    //假如消息A延迟的秒数为X，当前时间为Y，系统能保证A不会在X+Y之前执行。
    //但不能精确保证就是在精确的做到了在当前时间的X秒后运行，
    //如果需要精确的消息，如果我们需要严格让每个Message按照设计的时间执行，那就需要Alarm，类似闹钟的设计了
    private void postDelay(Runnable task,long millSecond)
    {
        //如果是延迟消息，生成的Message的执行时间是当前时间+延迟的秒数。
        Message message = new Message(System.currentTimeMillis() + millSecond, task);
        //使用While循环去找第一个执行事件在新创建的Message之前的Message,该新创建的Message就要插在它后面
        // 从尾部开始依次查找
        Message target = tail;
        //括号里的条件表示只要当前的节点的延迟时间比你传的要大，继续往前遍历，啥时候找到恰好小于你的，插在它后面即可。
        while (target.execTime>=message.execTime)
        {
            target = target.prev;
        }

        Message next = target.next;
        
     //插在target和next的之间，前后指针重新赋值。
        target.next = message;
        message.prev = target;
        message.next = next;
        next.prev = message;
    }
    
    //开启循环
    public  void startMessageQueue()
    {
        if (mMessageQueue==null)
        {
            mMessageQueue = new DelayMessageQueue();
            //开启死循环
           
        }
        mMessageQueue.executeTask();
       
    }
    //插入消息
    public void postMessage(Runnable runnable)
    {
        if (mMessageQueue==null)
            throw new RuntimeException("调用start");
        mMessageQueue.post(runnable);
        
    }
    //插入延迟消息
    public void postDelayMessage(Runnable runnable,long millDelay)
    {
        if (mMessageQueue==null)
            throw new RuntimeException("调用start");
        mMessageQueue.postDelay(runnable,millDelay);

    }
    
    
    
    //一个简化版的消息结构
    //除了runnable，还有一个该Message需要被执行的时间execTime，两个引用，指向该Message在链表中的前任节点和后继节点。
    
    class Message
    {
        //延迟时间
        public long execTime = -1;
        public Runnable task;

        //链表的前后指针
        public Message prev;
        public Message next;

        public Message(long execTime, Runnable task) 
        {
            this.execTime = execTime;
            this.task = task;
        }
    }
    
}
