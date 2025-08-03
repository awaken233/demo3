from sqlalchemy import Column, Integer, String, DateTime, Text
from sqlalchemy.sql import func
from database import Base

class User(Base):
    __tablename__ = "rp_user"
    
    id = Column(Integer, primary_key=True, autoincrement=True, comment='用户ID')
    username = Column(String(50), nullable=False, unique=True, comment='用户名')
    password = Column(String(100), nullable=False, comment='密码')
    salt = Column(String(20), nullable=False, comment='密码盐')
    create_time = Column(DateTime, nullable=False, default=func.current_timestamp(), comment='创建时间')
    update_time = Column(DateTime, nullable=False, default=func.current_timestamp(), onupdate=func.current_timestamp(), comment='修改时间')
    role = Column(String(20), nullable=False, default='user', comment='用户角色 (例如: user, admin)')
    status = Column(Integer, nullable=False, default=0, comment='用户状态 (0: active, 1: frozen)')
    nickname = Column(String(50), nullable=False, default='', comment='昵称')
    
    def __repr__(self):
        return f"<User(id={self.id}, username='{self.username}', nickname='{self.nickname}', role='{self.role}', status={self.status})>" 