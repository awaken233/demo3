import asyncio
import logging
import sys
import os
import json

# Assuming wiz_client.py is in the same directory or Python path
try:
    from wiz_client import AsyncWizOpenApi, ConfigStub
except ImportError:
    print("Error: Could not import AsyncWizOpenApi or ConfigStub from wiz_client.")
    print("Ensure wiz_client.py is in the same directory or your PYTHONPATH.")
    sys.exit(1)

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(asctime)s - %(name)s - %(levelname)s - %(message)s')
log = logging.getLogger(__name__)

async def run_example():
    """Demonstrates how to use the AsyncWizOpenApi client."""

    # --- IMPORTANT: Load Credentials Securely ---
    # Option 1: Environment Variables (Recommended)
    user_id = os.environ.get("WIZ_USER_ID")
    password = os.environ.get("WIZ_PASSWORD")
    group_name = os.environ.get("WIZ_GROUP_NAME") # Optional, defaults to personal KB

    # Option 2: Placeholder (Replace before running!)
    if not user_id:
        user_id = "YOUR_WIZ_EMAIL" # Replace with your email
        log.warning("WIZ_USER_ID environment variable not set. Using placeholder.")
    if not password:
        password = "YOUR_WIZ_PASSWORD" # Replace with your password
        log.warning("WIZ_PASSWORD environment variable not set. Using placeholder.")
    # group_name can be None if not set via environment

    # Basic check for placeholder values - essential!
    if user_id == "YOUR_WIZ_EMAIL" or password == "YOUR_WIZ_PASSWORD":
        log.error("Placeholder credentials detected. Please set environment variables WIZ_USER_ID and WIZ_PASSWORD, or edit example.py.")
        print("\nERROR: Placeholder credentials detected.")
        print("Please set environment variables WIZ_USER_ID and WIZ_PASSWORD,")
        print("or edit the user_id and password variables in example.py before running.")
        print("Example environment variable setup (Bash/Zsh):")
        print("  export WIZ_USER_ID='your_email@example.com'")
        print("  export WIZ_PASSWORD='your_password'")
        print("  # Optional: export WIZ_GROUP_NAME='Your Group Name'")
        return
    # ------------------------------------------

    config = ConfigStub(user_id=user_id, password=password, group_name=group_name)

    log.info(f"Attempting to connect with user: {config.user_id}, group: {config.group_name or 'Personal KB'}")

    # Optional: Pass session_kwargs to customize the underlying aiohttp session
    # For example, setting a default timeout for all requests in this client instance:
    # timeout = aiohttp.ClientTimeout(total=60) # 60 seconds total timeout
    # client_kwargs = {'timeout': timeout, 'headers': {'X-Custom-App': 'MyExample/1.0'}}
    client_kwargs = {} # No extra kwargs in this example

    try:
        # Use async with to ensure the session is properly closed
        async with AsyncWizOpenApi(config, **client_kwargs) as client:
            try:
                await client.connect() # Authenticate and fetch initial KB info
                log.info(f"Client connected successfully. KB GUID: {client.kb_guid}")

                # --- Example API Calls ---

                # 1. Get Note Count
                try:
                    count = await client.get_note_count()
                    log.info(f"Total notes in KB: {count}")
                except Exception as e:
                    log.error(f"Failed to get note count: {e}")

                # 2. Get Note List (fetch first 5 notes)
                notes = []
                try:
                    note_list_result = await client.get_note_list(version=0, count=5)
                    # Ensure 'list' exists and is iterable
                    notes = note_list_result.get('list') if isinstance(note_list_result.get('list'), list) else []
                    if notes:
                        log.info(f"Fetched {len(notes)} notes:")
                        for note in notes:
                            log.info(f"  - GUID: {note.get('docGuid')}, Title: {note.get('title')}")
                    else:
                        log.info("No notes found in the first batch.")
                except Exception as e:
                    log.error(f"Failed to get note list: {e}")

                # 3. Get Detail for the first note fetched (if any)
                if notes:
                    first_note_guid = notes[0].get('docGuid')
                    if first_note_guid:
                        log.info(f"Fetching detail for note: {first_note_guid}")
                        try:
                            detail = await client.get_note_detail(first_note_guid)
                            # Log only part of the detail to avoid large output
                            detail_info_str = json.dumps(detail.get('info', {})) # Get 'info' part as string
                            log.info(f"Note detail fetched (info part, max 300 chars): {detail_info_str[:300]}{'...' if len(detail_info_str) > 300 else ''}")
                        except Exception as detail_e:
                            log.error(f"Failed to get note detail for {first_note_guid}: {detail_e}")
                    else:
                         log.warning("Could not get GUID for the first note.")
                else:
                    # This log might be redundant if get_note_list already logged "No notes found"
                    # log.info("No notes fetched to get details for.")
                    pass # Already logged above if list was empty

            except Exception as e:
                # Catch errors during connect() or subsequent API calls within the context
                log.error(f"An error occurred after client initialization: {e}", exc_info=True)
                print(f"An error occurred during API operations: {e}")

    except Exception as e:
        # Catch errors during client instantiation (e.g., config error)
        log.error(f"An error occurred during client setup: {e}", exc_info=True)
        print(f"An error occurred during client setup: {e}")


if __name__ == "__main__":
    # --- ProactorEventLoop Policy for Windows ---
    # On Windows, the default asyncio event loop (SelectorEventLoop) might not
    # support certain features needed by libraries like aiohttp correctly,
    # especially for server-side operations or sometimes complex client scenarios.
    # ProactorEventLoop is an alternative event loop available on Windows
    # that often provides better compatibility in these cases.
    # This check ensures that if the code is run on Windows, it uses ProactorEventLoop.
    # For other OS (Linux, macOS), the default loop is usually sufficient.
    if sys.platform == "win32":
        # Check if ProactorEventLoop is available (usually is on recent Python 3 on Windows)
        if hasattr(asyncio, 'ProactorEventLoop'):
             # Setting the policy changes the default loop for asyncio.run()
            try:
                asyncio.set_event_loop_policy(asyncio.ProactorEventLoopPolicy())
                log.info("Running on Windows. Set asyncio event loop policy to ProactorEventLoopPolicy.")
            except Exception as policy_e:
                log.error(f"Failed to set ProactorEventLoopPolicy: {policy_e}. Using default loop.")
        else:
            log.warning("Running on Windows, but ProactorEventLoop is not available or cannot be set. Using default loop.")
    # --------------------------------------------

    print("\nRunning AsyncWizOpenApi Example...")
    print("-" * 30)
    # Use asyncio.run to execute the async function
    asyncio.run(run_example())
    print("-" * 30)
    print("Example finished.") 