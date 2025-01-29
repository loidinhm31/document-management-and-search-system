import numpy as np
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.multiclass import OneVsRestClassifier
from sklearn.svm import LinearSVC
from sklearn.preprocessing import MultiLabelBinarizer
from typing import List, Set
import re
from underthesea import word_tokenize
import nltk
from nltk.corpus import stopwords
import pickle
import os
from datetime import datetime
import pandas as pd

# Download required NLTK data
nltk.download('stopwords', quiet=True)
nltk.download('punkt', quiet=True)


class SimpleDocumentClassifier:
    def __init__(self, model_dir="models"):
        self.model_dir = model_dir
        if not os.path.exists(model_dir):
            os.makedirs(model_dir)

        # Initialize stopwords
        self.en_stopwords = list(stopwords.words('english'))
        self.vn_stopwords = [
            'và', 'hoặc', 'trong', 'các', 'của', 'có', 'được', 'với',
            'những', 'để', 'theo', 'từ', 'về', 'là', 'cho', 'không',
            'này', 'khi', 'tới', 'bởi', 'đã', 'sẽ', 'tại', 'đến',
            'một', 'như', 'mà', 'nhiều', 'trên', 'người', 'thì'
        ]

        self.vectorizer = TfidfVectorizer(
            max_features=5000,
            ngram_range=(1, 2),
            min_df=1,
            stop_words=None
        )

        self.classifier = OneVsRestClassifier(LinearSVC(
            random_state=42,
            C=1.0,
            class_weight='balanced'
        ))
        self.mlb = MultiLabelBinarizer()
        self.is_trained = False

        # Try to load the latest model
        self.load_latest_model()

    def preprocess_vietnamese(self, text: str) -> str:
        text = text.lower()
        text = re.sub(r'[^\w\s]', ' ', text)
        tokens = word_tokenize(text)
        tokens = [t for t in tokens if t not in self.vn_stopwords]
        return ' '.join(tokens)

    def preprocess_english(self, text: str) -> str:
        text = text.lower()
        text = re.sub(r'[^\w\s]', ' ', text)
        tokens = nltk.word_tokenize(text)
        tokens = [t for t in tokens if t not in self.en_stopwords]
        return ' '.join(tokens)

    def train(self, training_data_path: str):
        """Train the classifier using data from CSV file"""
        # Read the CSV file
        df = pd.read_csv(training_data_path)

        # Ensure categories are in the correct format
        # Split categories by both comma and space
        df['categories'] = df['categories'].apply(lambda x: [
            cat.strip()
            for cat in re.split('[,\\s]+', str(x))
            if cat.strip()
        ])

        texts = df['text'].tolist()
        categories = df['categories'].tolist()
        languages = df['language'].tolist()

        # Add validation
        if not texts or not categories:
            raise ValueError("Empty training data")

        # Preprocess texts based on language
        processed_texts = [
            self.preprocess_english(text) if lang == 'english' else self.preprocess_vietnamese(text)
            for text, lang in zip(texts, languages)
        ]

        # Transform categories
        y = self.mlb.fit_transform(categories)

        # Create TF-IDF features
        X = self.vectorizer.fit_transform(processed_texts).toarray()

        # Train the classifier
        self.classifier.fit(X, y)
        self.is_trained = True

        # Save the model
        self.save_model()

    def predict_categories(self, text: str, language: str = 'english', threshold: float = 0.3) -> List[dict]:
        """Predict categories for a given document with confidence scores"""
        if not self.is_trained:
            raise ValueError("Classifier must be trained before making predictions")

        # Preprocess based on language
        if language == 'vietnamese':
            processed_text = self.preprocess_vietnamese(text)
        else:
            processed_text = self.preprocess_english(text)

        X = self.vectorizer.transform([processed_text])

        # Get prediction probabilities
        prediction_scores = self.classifier.decision_function(X.toarray())

        # Convert scores to probabilities using sigmoid function
        probabilities = 1 / (1 + np.exp(-prediction_scores))

        # Get categories with confidence scores
        categories_with_scores = []
        for idx, prob in enumerate(probabilities[0]):
            if prob >= threshold:
                category = self.mlb.classes_[idx]
                categories_with_scores.append({
                    'category': category,
                    'confidence': float(prob)
                })

        # Sort by confidence
        return sorted(categories_with_scores, key=lambda x: x['confidence'], reverse=True)

    def extract_tags(self, text: str, language: str = 'english', top_n: int = 5) -> Set[str]:
        """Extract relevant tags from document content"""
        if language == 'vietnamese':
            processed_text = self.preprocess_vietnamese(text)
        else:
            processed_text = self.preprocess_english(text)

        # Get TF-IDF scores
        tfidf_matrix = self.vectorizer.transform([processed_text])
        feature_names = self.vectorizer.get_feature_names_out()

        # Get top scoring terms
        scores = zip(feature_names, np.asarray(tfidf_matrix.sum(axis=0)).ravel())
        sorted_scores = sorted(scores, key=lambda x: x[1], reverse=True)

        return {term for term, score in sorted_scores[:top_n] if score > 0}

    def save_model(self):
        """Save the current model with timestamp"""
        if not self.is_trained:
            raise ValueError("Cannot save untrained model")

        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filepath = os.path.join(self.model_dir, f"classifier_model_{timestamp}.pkl")

        model_data = {
            'vectorizer': self.vectorizer,
            'classifier': self.classifier,
            'mlb': self.mlb,
            'is_trained': self.is_trained
        }

        with open(filepath, 'wb') as f:
            pickle.dump(model_data, f)
        print(f"Model saved to {filepath}")

    def load_latest_model(self) -> bool:
        """Load the most recent model if available"""
        if not os.path.exists(self.model_dir):
            return False

        model_files = [f for f in os.listdir(self.model_dir)
                       if f.startswith("classifier_model_") and f.endswith(".pkl")]

        if not model_files:
            return False

        latest_model = max(model_files)
        filepath = os.path.join(self.model_dir, latest_model)

        try:
            with open(filepath, 'rb') as f:
                model_data = pickle.load(f)

            self.vectorizer = model_data['vectorizer']
            self.classifier = model_data['classifier']
            self.mlb = model_data['mlb']
            self.is_trained = model_data['is_trained']
            print(f"Loaded model from {filepath}")
            return True

        except Exception as e:
            print(f"Error loading model: {e}")
            return False


def main():
    # Initialize classifier
    classifier = SimpleDocumentClassifier()

    # Train with CSV data if not already trained
    if not classifier.is_trained:
        print("Training new model...")
        classifier.train('document_training_data.csv')

    # Test with English document
    en_test_doc = """
    Advanced Algorithms Lecture
    This lecture covers sorting algorithms and data structures.
    We will discuss complexity analysis and implementation details.
    Topics include QuickSort, MergeSort, and Binary Trees.
    """
    en_categories = classifier.predict_categories(en_test_doc, 'english')
    en_tags = classifier.extract_tags(en_test_doc, 'english')

    print("\nEnglish document results:")
    print("Predictions with confidence:")
    for pred in en_categories:
        print(f"Category: {pred['category']}, Confidence: {pred['confidence']:.2f}")
    print(f"Tags: {en_tags}")

    # Test with Vietnamese document
    vn_test_doc = """
    Bài giảng Giải thuật Nâng cao
    Bài giảng này bao gồm các thuật toán sắp xếp và cấu trúc dữ liệu.
    Chúng ta sẽ thảo luận về phân tích độ phức tạp và chi tiết triển khai.
    Các chủ đề bao gồm QuickSort, MergeSort và Cây Nhị Phân.
    """
    vn_categories = classifier.predict_categories(vn_test_doc, 'vietnamese')
    vn_tags = classifier.extract_tags(vn_test_doc, 'vietnamese')

    print("\nVietnamese document results:")
    print("Predictions with confidence:")
    for pred in vn_categories:
        print(f"Category: {pred['category']}, Confidence: {pred['confidence']:.2f}")
    print(f"Tags: {vn_tags}")


if __name__ == "__main__":
    main()