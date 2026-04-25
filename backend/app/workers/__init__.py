"""Background-worker abstraction — `run_async` runs jobs inline now; swap to Celery/Dramatiq in production."""
from __future__ import annotations

import asyncio
from collections.abc import Awaitable
from typing import TypeVar

T = TypeVar("T")


async def run_async(coro: Awaitable[T]) -> T:
    """Inline runner. Replace with proper queue dispatch in production."""
    return await coro


def schedule(_coro: Awaitable[object]) -> None:
    """Fire-and-forget scheduling for now. TODO: route to Celery/Dramatiq."""
    asyncio.create_task(_coro)  # type: ignore[arg-type]
