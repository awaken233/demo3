from functools import wraps

def rocketmq_consumer():
    def decorator(func):

        print("这是装饰器内部的代码，只有调用函数时才会执行")

        @wraps(func)
        def wrapper(*args, **kwargs):
            print("这是装饰器内部的代码，只有调用函数时才会执行")
            return func(*args, **kwargs)
        return wrapper
    return decorator