const LoadingSpinner = ({ message = 'Loading...' }) => {
  return (
    <div className="d-flex flex-column justify-content-center align-items-center" style={{ minHeight: '400px' }}>
      <div className="spinner-border text-primary mb-3" role="status" style={{ width: '3rem', height: '3rem' }}>
        <span className="visually-hidden">Loading...</span>
      </div>
      <p className="text-muted">{message}</p>
    </div>
  );
};

export default LoadingSpinner;
