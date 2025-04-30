from typing import Any, Dict, Optional, Type

import aiohttp
import logging


class HttpClient:
    def __init__(self, **session_kwargs: Any) -> None:
        self._session_kwargs = session_kwargs
        self._session: Optional[aiohttp.ClientSession] = None

    async def __aenter__(self) -> "HttpClient":
        if self._session is None or self._session.closed:
             self._session = aiohttp.ClientSession(**self._session_kwargs)
        return self

    async def __aexit__(
        self,
        exc_type: Optional[Type[BaseException]],
        exc_val: Optional[BaseException],
        exc_tb: Optional[Any],
    ) -> None:
        await self.close()

    async def close(self) -> None:
        if self._session and not self._session.closed:
            await self._session.close()
            self._session = None

    async def _get_session(self) -> aiohttp.ClientSession:
        if self._session is None or self._session.closed:
            self._session = aiohttp.ClientSession(**self._session_kwargs)
        return self._session

    async def get(self, url: str, **kwargs: Any) -> Any:
        session = await self._get_session()
        async with session.get(url, **kwargs) as response:
            response.raise_for_status()
            content_type = response.headers.get("Content-Type", "")
            if "application/json" in content_type:
                return await response.json()
            else:
                return await response.text()

    async def get_bytes(self, url: str, **kwargs: Any) -> bytes:
        session = await self._get_session()
        async with session.get(url, **kwargs) as response:
            response.raise_for_status()
            return await response.read()

    async def post(self, url: str, **kwargs: Any) -> Any:
        session = await self._get_session()
        async with session.post(url, **kwargs) as response:
            response.raise_for_status()
            content_type = response.headers.get("Content-Type", "")
            if "application/json" in content_type:
                return await response.json()
            else:
                return await response.text()