import React from 'react';

const ErrorMessage = ({ message }) => {
  if (!message) return null;
  return (
    <div style={{ color: 'red', margin: '10px 0', padding: '10px', border: '1px solid red', borderRadius: '4px', backgroundColor: '#fee' }}>
      {message}
    </div>
  );
};

export default ErrorMessage;
