from rocketmq_consumer import rocketmq_consumer


def test_function():
    print("这是函数内部的代码，只有调用函数时才会执行")

@rocketmq_consumer()
def test_function3():
    print("test_function3.....")