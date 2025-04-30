import asyncio
import aiohttp
import time
import json # 导入 json 以便更好地显示数据
from aiohttp import ClientTimeout # 导入 ClientTimeout

async def post_fetch(session, url, data):
    """异步发送单个 POST 请求"""
    try:
        # 将字典数据转换为 JSON 字符串发送，如果需要的话
        # 或者直接发送字典，aiohttp 会处理 Content-Type 为 application/x-www-form-urlencoded 或 multipart/form-data
        # 这里我们假设发送 JSON
        start_req_time = time.time()
        async with session.post(url, json=data) as response:
            response_text = await response.text() # 读取响应体
            end_req_time = time.time()
            duration = end_req_time - start_req_time
            print(f"Status {response.status} from POST {url} with data: {json.dumps(data)}. Duration: {duration:.3f}s. Response: {response_text[:100]}...") # 打印部分响应和数据
            return response.status, response_text[:100] # 返回状态和部分响应
    except aiohttp.ClientError as e:
        print(f"Error posting to {url} with data {json.dumps(data)}: {e}")
        return None, str(e)
    except Exception as e: # 捕获其他可能的异常，例如 JSON 序列化
        print(f"General error posting to {url} with data {json.dumps(data)}: {e}")
        return None, str(e)

async def main(requests_config):
    """并发发送多个 POST 请求 (包含超时设置)"""
    async with aiohttp.ClientSession(timeout=ClientTimeout(total=10)) as session:
        tasks = [post_fetch(session, req['url'], req['data']) for req in requests_config]
        start_time = time.time()
        results = await asyncio.gather(*tasks, return_exceptions=True)
        end_time = time.time()
        print(f"\nSent {len(requests_config)} POST requests in {end_time - start_time:.2f} seconds.")
        # 过滤掉 gather 可能直接返回的异常对象
        successful_results = [res for res in results if not isinstance(res, Exception)]
        print(f"Successful responses: {len(successful_results)}")
        # 你可以根据需要进一步处理 results，例如区分成功和失败的请求
        # print("Results:", results) # 打印详细结果，可能很多

if __name__ == "__main__":
    # 目标 URL (使用 httpbin.org/post 进行测试，它会回显 POST 数据)
    base_url = "https://httpbin.org/post"

    # 准备不同的 POST 数据负载
    requests_data = [
        {"param1": "value1", "param2": 10},
        {"param1": "value2", "param2": 20, "extra": True},
        {"user": "alice", "action": "login"},
        {"user": "bob", "action": "update", "data": {"field": "email", "value": "bob@example.com"}},
        {"item_id": 123, "quantity": 5},
        {"item_id": 456, "quantity": 1},
        {"query": "asyncio concurrent post", "page": 1},
        {"query": "aiohttp post example", "page": 1},
        {"config": {"timeout": 5000, "retries": 3}},
        {"config": {"timeout": 10000, "retries": 1}},
    ] * 5 # 重复数据以增加并发量 (共 50 个请求)

    # 创建请求配置列表
    post_requests_config = [{'url': base_url, 'data': payload} for payload in requests_data]

    print(f"Starting {len(post_requests_config)} concurrent POST requests to {base_url}...")
    asyncio.run(main(post_requests_config)) 