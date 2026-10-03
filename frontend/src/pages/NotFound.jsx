import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const NotFound = () => {
  const { user } = useAuth();
  
  let returnLink = "/";
  if (user && user.roles) {
    const roles = user.roles.map(r => r.replace(/^ROLE_/i, '').toUpperCase());
    if (roles.includes('ADMIN')) returnLink = '/admin/dashboard';
    else if (roles.includes('MANAGER')) returnLink = '/manager/dashboard';
    else if (roles.includes('CREDIT_OFFICER')) returnLink = '/officer/dashboard';
    else if (roles.includes('CUSTOMER')) returnLink = '/customer/dashboard';
  }

  return (
    <div style={{ textAlign: 'center', marginTop: '100px' }}>
      <h1>404 - Page Not Found</h1>
      <p>The page you are looking for does not exist.</p>
      <Link to={returnLink} style={{ color: '#0056b3', textDecoration: 'none', fontWeight: 'bold' }}>
        Return to Dashboard
      </Link>
    </div>
  );
};

export default NotFound;
