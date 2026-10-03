import React from 'react';
import { Outlet, useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../context/AuthContext';

const DashboardLayout = () => {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    navigate('/login');
  };

  const roles = user?.roles?.map(r => r.replace(/^ROLE_/i, '').toUpperCase()) || [];
  const isManager = roles.includes('MANAGER');
  const isOfficer = roles.includes('CREDIT_OFFICER');
  const isCustomer = roles.includes('CUSTOMER');
  const isAdmin = roles.includes('ADMIN');

  const navLinkStyle = (path) => {
    let isActive = location.pathname === path;
    if (path.endsWith('/applications') && location.pathname.startsWith(path)) {
      isActive = true;
    }
    if (path.endsWith('/loans') && location.pathname.startsWith(path)) {
      isActive = true;
    }
    if (path.endsWith('/disbursements') && location.pathname.startsWith(path)) {
      isActive = true;
    }
    
    return {
      marginRight: '1.5rem',
      textDecoration: 'none',
      color: isActive ? '#0056b3' : '#555',
      fontWeight: isActive ? 'bold' : 'normal',
      borderBottom: isActive ? '2px solid #0056b3' : 'none',
      paddingBottom: '5px'
    };
  };

  return (
    <div className="dashboard-layout" style={{ minHeight: '100vh', display: 'flex', flexDirection: 'column', backgroundColor: '#f9f9f9' }}>
      <header style={{ padding: '1rem 2rem', backgroundColor: '#fff', borderBottom: '1px solid #eee', color: '#333', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h2 style={{ margin: 0, color: '#0056b3' }}>LoanFlow</h2>
        <div style={{ display: 'flex', alignItems: 'center' }}>
          <span style={{ marginRight: '1.5rem', cursor: 'pointer', color: '#333' }} onClick={() => {
            if (isCustomer && !isManager && !isOfficer && !isAdmin) navigate('/customer/profile');
          }}>
            Welcome, <span style={{ fontWeight: 'bold' }}>{user?.firstName} {user?.lastName}</span>
          </span>
          <button onClick={handleLogout} style={{ padding: '6px 12px', backgroundColor: '#f8f9fa', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer' }}>Logout</button>
        </div>
      </header>

      {isAdmin ? (
        <nav style={{ padding: '1rem 2rem', backgroundColor: '#fff', borderBottom: '1px solid #eee', display: 'flex' }}>
          <Link to="/admin/dashboard" style={navLinkStyle('/admin/dashboard')}>Dashboard</Link>
          <Link to="/admin/disbursements" style={navLinkStyle('/admin/disbursements')}>Disbursements</Link>
        </nav>
      ) : isManager ? (
        <nav style={{ padding: '1rem 2rem', backgroundColor: '#fff', borderBottom: '1px solid #eee', display: 'flex' }}>
          <Link to="/manager/dashboard" style={navLinkStyle('/manager/dashboard')}>Dashboard</Link>
          <Link to="/manager/applications" style={navLinkStyle('/manager/applications')}>Applications</Link>
        </nav>
      ) : isOfficer ? (
        <nav style={{ padding: '1rem 2rem', backgroundColor: '#fff', borderBottom: '1px solid #eee', display: 'flex' }}>
          <Link to="/officer/dashboard" style={navLinkStyle('/officer/dashboard')}>Dashboard</Link>
          <Link to="/officer/applications" style={navLinkStyle('/officer/applications')}>Applications</Link>
        </nav>
      ) : isCustomer ? (
        <nav style={{ padding: '1rem 2rem', backgroundColor: '#fff', borderBottom: '1px solid #eee', display: 'flex' }}>
          <Link to="/customer/dashboard" style={navLinkStyle('/customer/dashboard')}>Dashboard</Link>
          <Link to="/customer/loan-products" style={navLinkStyle('/customer/loan-products')}>Loan Products</Link>
          <Link to="/customer/applications" style={navLinkStyle('/customer/applications')}>My Applications</Link>
          <Link to="/customer/loans" style={navLinkStyle('/customer/loans')}>My Loans</Link>
          <Link to="/customer/profile" style={navLinkStyle('/customer/profile')}>Profile</Link>
        </nav>
      ) : null}

      <main style={{ padding: '2rem', flexGrow: 1 }}>
        <Outlet />
      </main>
    </div>
  );
};

export default DashboardLayout;
