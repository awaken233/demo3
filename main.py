from fastapi import FastAPI, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List
import uvicorn

from database import get_db, engine
from models import Base, User

# 创建FastAPI应用
app = FastAPI(
    title="FastAPI SQLAlchemy 测试工程",
    description="一个简单的FastAPI和SQLAlchemy测试项目",
    version="1.0.0"
)

@app.get("/")
def read_root():
    return {"message": "FastAPI SQLAlchemy 测试工程"}

@app.get("/users/", response_model=List[dict])
def read_users(db: Session = Depends(get_db)):
    print("read_users ....")
    """查询所有用户"""
    users = db.query(User).limit(1).all()
    print("read_users end ....")
    db.commit()


    print("read_users2 ....")
    """查询所有用户"""
    users = db.query(User).limit(1).all()
    print("read_users2 end ....")
    db.commit()

    return []

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True) 