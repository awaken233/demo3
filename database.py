import os
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base
from sqlalchemy.pool import QueuePool
from typing import Generator

# MySQL数据库连接配置
DATABASE_URL = os.getenv("DATABASE_URL", "")

# 创建数据库引擎
engine = create_engine(
    DATABASE_URL,
    # 连接池配置
    poolclass=QueuePool,
    pool_size=20,          # 连接池大小
    max_overflow=0,        # 最大溢出连接数
    pool_pre_ping=True,    # 连接前检查
    pool_recycle=3600,     # 连接回收时间(秒)
    # 查询优化
    echo=True,            # 生产环境不打印SQL
    future=True            # 使用 2.0 风格API
)

# 会话工厂
SessionLocal = sessionmaker(
    bind=engine,
    autocommit=False,
    autoflush=False,
    expire_on_commit=False  # 重要：防止对象在提交后过期
)

# 声明基类
Base = declarative_base()

# FastAPI 依赖注入函数
def get_db() -> Generator:
    db = SessionLocal()
    try:
        yield db  # 控制作用域
        db.commit()
    except:
        db.rollback()
        raise
    finally:
        db.close() 