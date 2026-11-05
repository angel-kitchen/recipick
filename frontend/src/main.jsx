import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App';
import { AuthProvider } from './auth/AuthContext';
import './styles/tokens.css';
import './styles/global.css';
import './styles/layout.css';
import './styles/home.css';
import './styles/shop.css';
import './styles/admin.css';
import './styles/membership-intro.css';
import './styles/band-scenes.css';
import './styles/theme.css';
import './styles/home-wire.css';
import './styles/recipe-chat.css';

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <AuthProvider>
        <App />
      </AuthProvider>
    </BrowserRouter>
  </StrictMode>,
);
