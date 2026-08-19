import { AuthProvider } from './context/AuthContext';
import AppRoutes from './routes/AppRoutes';
import { Toaster } from 'react-hot-toast';
import 'bootstrap/dist/css/bootstrap.min.css';
import 'bootstrap/dist/js/bootstrap.bundle.min.js';

function App() {
  return (
    <AuthProvider>
      <AppRoutes />
      <Toaster 
        position="top-right"
        toastOptions={{
          duration: 3000,
          style: {
            background: '#1a202c',
            color: '#fff',
            padding: '16px',
            borderRadius: '0.5rem',
            boxShadow: '0 10px 15px -3px rgba(0, 0, 0, 0.3)',
            fontWeight: '500',
            border: '1px solid rgba(255, 255, 255, 0.1)',
          },
          success: {
            duration: 3000,
            iconTheme: {
              primary: '#48bb78',
              secondary: '#fff',
            },
            style: {
              background: '#1a202c',
              color: '#fff',
            }
          },
          error: {
            duration: 4000,
            iconTheme: {
              primary: '#f56565',
              secondary: '#fff',
            },
            style: {
              background: '#1a202c',
              color: '#fff',
            }
          },
        }}
      />
    </AuthProvider>
  );
}

export default App;
