import json
import logging
from typing import Any, Optional, Type, Dict

from http_client import HttpClient

# Configure logging (basic example, adjust as needed)
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(levelname)s - %(message)s')
log = logging.getLogger(__name__)

class ConfigStub: # Placeholder for the config object structure
    def __init__(self, user_id, password):
        self.user_id = user_id
        self.password = password

class AsyncWizOpenApi:
    """Asynchronous client for the WizNote Open API based on the provided sync code."""
    # account server URL
    AS_URL = 'https://as.wiz.cn'

    def __init__(self, config: ConfigStub, **session_kwargs: Any):
        """
        Initializes the AsyncWizOpenApi client.

        Args:
            config: Configuration object containing user_id, password.
            **session_kwargs: Optional keyword arguments passed to the underlying HttpClient
                              (and subsequently to aiohttp.ClientSession).
        """
        if not all([hasattr(config, 'user_id'), hasattr(config, 'password')]):
            raise ValueError("Config object must have 'user_id' and 'password' attributes.")
        
        self.user_id: str = config.user_id
        self.password: str = config.password

        # API state, populated after connection
        self.token: str = ''
        self.kb_server: str = ''
        self.kb_guid: str = ''
        self.user_guid: str = ''
        self.domain: str = ''

        # Internal HttpClient instance
        self._http_client = HttpClient(**session_kwargs)

    async def __aenter__(self) -> "AsyncWizOpenApi":
        """Enters the asynchronous context, initializing the underlying session."""
        await self._http_client.__aenter__()
        return self

    async def __aexit__(
        self,
        exc_type: Optional[Type[BaseException]],
        exc_val: Optional[BaseException],
        exc_tb: Optional[Any],
    ) -> None:
        """Exits the asynchronous context, closing the underlying session."""
        await self._http_client.__aexit__(exc_type, exc_val, exc_tb)

    async def connect(self) -> None:
        """
        Authenticates with the WizNote service and fetches initial state.
        Must be called after instantiation or use async with.
        """
        log.info(f"Attempting to login user: {self.user_id}")
        login_data = await self._login()
        
        self.token = login_data['result']['token']
        self.kb_server = login_data['result']['kbServer']
        self.kb_guid = login_data['result']['kbGuid']
        self.user_guid = login_data['result']['userGuid']
        # Ensure kb_server has https:// prefix for consistency
        if not self.kb_server.startswith(('http://', 'https://')):
            self.kb_server = 'https://' + self.kb_server
        # Extract domain without protocol
        self.domain = self.kb_server.split('//')[-1]

        log.info(f"Login successful. Token acquired. KB Server: {self.kb_server}, KB Guid: {self.kb_guid}")
        
        log.info(f"Initialization complete. Using KB Server: {self.kb_server}, KB Guid: {self.kb_guid}")

    async def _login(self) -> Dict[str, Any]:
        """Performs the login request."""
        login_url = f'{self.AS_URL}/as/user/login'
        try:
            data = await self._http_client.post(login_url, data={'userId': self.user_id, 'password': self.password})
            log.info(f'Login response: {json.dumps(data)}') # Assumes data is dict/json serializable
            if data.get('returnCode') != 200:
                raise Exception(f'Login failed: API response indicates error: {data}')
            return data
        except Exception as e:
            log.error(f"Login request failed: {e}", exc_info=True)
            # Re-raise exception after logging to indicate failure
            raise Exception(f'Login request failed: {e}')

    async def get_note_list(self, version: int, count: int) -> Dict[str, Any]:
        note_list_url = f'{self.kb_server}/ks/note/list/version/{self.kb_guid}'
        try:
            data = await self._http_client.get(note_list_url, params={'version': version, 'count': count}, headers={'X-Wiz-Token': self.token})
            # Note: Original sync code checked returnCode, assuming HttpClient does status check, we check returnCode here.
            if data.get('returnCode') != 200:
                 raise Exception(f'Failed to get note list: API response indicates error: {data}')
            return data.get('result', {}) # Return empty dict if result is missing
        except Exception as e:
             log.error(f"Get note list request failed: {e}", exc_info=True)
             raise Exception(f'Get note list request failed: {e}')
