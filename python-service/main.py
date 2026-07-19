from fastapi import FastAPI
from routers import ai_analysis, internal

app = FastAPI(
    title="WSI Platform - Image Processing Service",
    description="Internal microservice for WSI tile generation, mask rendering, and patch extraction. "
                "NOT exposed to the public — only callable from Spring Boot backend on localhost.",
    version="0.1.0",
    docs_url="/docs",   # disable in production if needed
)

app.include_router(internal.router, prefix="/internal", tags=["internal"])
app.include_router(
    ai_analysis.router,
    prefix="/internal/ai",
    tags=["ai-analysis"],
)


@app.get("/health")
def health():
    return {"status": "ok"}
