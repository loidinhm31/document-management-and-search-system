import os
import pickle
import re
from contextlib import asynccontextmanager
from typing import Dict, List

import nltk
import pandas as pd
import scipy
import uvicorn
from fastapi import FastAPI, HTTPException, Depends, Security
from fastapi.security import APIKeyHeader
from nltk.corpus import stopwords
from pydantic import BaseModel
from scipy.sparse import hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from underthesea import word_tokenize

# Download required NLTK data
nltk.download('stopwords', quiet=True)
nltk.download('punkt', quiet=True)

# Constants
API_KEY = "your-secret-key"  # Change this to your secure key
MODEL_DIR = "models"

# Initialize stopwords
en_stopwords = set(stopwords.words('english'))
vn_stopwords = {
    'và', 'của', 'có', 'được', 'trong', 'đã', 'là', 'lúc', 'với', 'theo',
    'tới', 'về', 'làm', 'để', 'từ', 'những', 'hay', 'khi', 'sau', 'như',
    'trên', 'vào', 'phải', 'bị', 'cho', 'đến', 'nếu', 'tại', 'nhưng', 'mà',
    'thì', 'hoặc', 'vẫn', 'bởi', 'này', 'các', 'nhiều', 'thêm', 'vừa', 'cũng',
    'nên', 'việc', 'nói', 'nhất', 'đều', 'theo', 'cùng', 'đang', 'chỉ', 'vì',
    'còn', 'giữa', 'thuộc', 'quá', 'sẽ'
}

# Security
api_key_header = APIKeyHeader(name="X-API-Key")

# Model components to be loaded in lifespan
model_components = {}


# Request/Response Models
class DocumentRequest(BaseModel):
    text: str
    filename: str
    language: str


class CategoryPrediction(BaseModel):
    category: str
    confidence: float


class PredictionResponse(BaseModel):
    predictions: List[CategoryPrediction]


def preprocess_vietnamese(text: str) -> str:
    """Preprocess Vietnamese text"""
    text = text.lower()
    text = re.sub(r'[^\w\s]', ' ', text)
    tokens = word_tokenize(text)
    tokens = [t for t in tokens if t not in vn_stopwords]
    return ' '.join(tokens)


def preprocess_english(text: str) -> str:
    """Preprocess English text"""
    text = text.lower()
    text = re.sub(r'[^\w\s]', ' ', text)
    tokens = nltk.word_tokenize(text)
    tokens = [t for t in tokens if t not in en_stopwords]
    return ' '.join(tokens)


def process_filename(filename: str) -> str:
    """Process filename into features"""
    name, ext = os.path.splitext(filename.lower())
    name = re.sub(r'[^\w\s]', ' ', name)
    ext_feature = f"extension_{ext[1:] if ext else 'none'}"
    return f"{name} {ext_feature}"


def extract_features(df: pd.DataFrame,
                     text_vectorizer: TfidfVectorizer = None,
                     filename_vectorizer: TfidfVectorizer = None) -> scipy.sparse.csr_matrix:
    """Extract features from input data"""
    # Process text based on language
    processed_texts = [
        preprocess_english(text) if lang == 'en' else preprocess_vietnamese(text)
        for text, lang in zip(df['text'], df['language'])
    ]

    # Process filenames
    processed_filenames = [process_filename(fname) for fname in df['filename']]

    # Transform using pre-fitted vectorizers
    text_features = text_vectorizer.transform(processed_texts)
    filename_features = filename_vectorizer.transform(processed_filenames)

    # Combine features
    return hstack([text_features, filename_features]).tocsr()


def get_api_key(api_key: str = Security(api_key_header)) -> str:
    if api_key == API_KEY:
        return api_key
    raise HTTPException(status_code=403, detail="Invalid API key")


def load_model(model_dir: str = MODEL_DIR) -> tuple:
    """Load the most recent model"""
    if not os.path.exists(model_dir):
        raise FileNotFoundError(f"Model directory {model_dir} not found")

    model_files = [f for f in os.listdir(model_dir)
                   if f.startswith("classifier_model_") and f.endswith(".pkl")]

    if not model_files:
        raise FileNotFoundError("No model files found")

    latest_model = max(model_files)
    with open(os.path.join(model_dir, latest_model), 'rb') as f:
        model_data = pickle.load(f)

    return (
        model_data['feature_processors'],
        model_data['classifier'],
        model_data['label_binarizer']
    )


def predict_categories(text: str, filename: str, language: str) -> List[Dict[str, float]]:
    """Predict categories with confidence scores"""
    if language not in {'en', 'vi'}:
        raise ValueError("Invalid language code. Use 'en' for English or 'vi' for Vietnamese")

    # Create DataFrame with single document
    df = pd.DataFrame({
        'text': [text],
        'filename': [filename],
        'language': [language]
    })

    # Extract features
    X = extract_features(
        df,
        text_vectorizer=model_components['feature_processors']['text_vectorizer'],
        filename_vectorizer=model_components['feature_processors']['filename_vectorizer']
    )

    # Get probabilities for each category
    probabilities = model_components['classifier'].predict_proba(X)

    # Create list of categories with their probabilities
    predictions = []
    for idx, category in enumerate(model_components['label_binarizer'].classes_):
        confidence = float(probabilities[idx][0][1])
        predictions.append({
            'category': category,
            'confidence': confidence
        })

    # Sort by confidence in descending order
    predictions.sort(key=lambda x: x['confidence'], reverse=True)
    return predictions


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Load model components on startup
    try:
        feature_processors, classifier, label_binarizer = load_model()
        model_components.update({
            'feature_processors': feature_processors,
            'classifier': classifier,
            'label_binarizer': label_binarizer
        })
        print("Model loaded successfully")
    except Exception as e:
        print(f"Failed to load model: {e}")
        raise e

    yield  # yield control back to FastAPI

    # Cleanup (if needed) on shutdown
    model_components.clear()
    print("Cleanup completed")


app = FastAPI(lifespan=lifespan)


@app.post("/predict", response_model=PredictionResponse)
async def predict(request: DocumentRequest, api_key: str = Depends(get_api_key)):
    print("Predicting for:", request.text)
    try:
        predictions = predict_categories(
            request.text,
            request.filename,
            request.language
        )

        return PredictionResponse(
            predictions=[
                CategoryPrediction(category=pred['category'], confidence=pred['confidence'])
                for pred in predictions
            ]
        )
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8000)