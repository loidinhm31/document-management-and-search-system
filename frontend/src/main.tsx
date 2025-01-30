import "@/index.css";
import "@/i18n/i18n";

import { StrictMode } from "react";
import { createRoot } from "react-dom/client";

import App from "@/App";

import { Provider } from 'react-redux';
import { store } from "@/store";

createRoot(document.getElementById('root')!).render(
  <Provider store={store}>
    <App />
  </Provider>
);