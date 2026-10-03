import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import { useAuth } from '../../context/AuthContext';
import StatCard from '../../components/customer/StatCard';
import LoanSummaryCard from '../../components/customer/LoanSummaryCard';
import ApplicationStatusCard from '../../components/customer/ApplicationStatusCard';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const CustomerDashboard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [overview, setOverview] = useState(null);
  const [loans, setLoans] = useState([]);
  const [applications, setApplications] = useState([]);

  useEffect(() => {
    const fetchDashboardData = async () => {
      try {
        setLoading(true);
        const [overviewRes, loansRes, appsRes] = await Promise.allSettled([
          customerApi.getRepaymentOverview(),
          customerApi.getMyLoans(0, 5),
          customerApi.getMyApplications(0, 5)
        ]);

        if (overviewRes.status === 'fulfilled') {
          setOverview(overviewRes.value.data);
        }
        if (loansRes.status === 'fulfilled') {
          setLoans(loansRes.value.data.content || []);
        }
        if (appsRes.status === 'fulfilled') {
          setApplications(appsRes.value.data.content || []);
        }
        
      } catch (err) {
        console.error('Error fetching dashboard data', err);
        setError('Failed to load dashboard data. Please try again later.');
      } finally {
        setLoading(false);
      }
    };

    fetchDashboardData();
  }, []);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <header style={{ marginBottom: '30px' }}>
        <h1 style={{ margin: '0 0 5px 0' }}>Welcome, {user?.firstName || 'Customer'}</h1>
        <p style={{ margin: 0, color: '#666' }}>Here is a summary of your accounts and applications.</p>
      </header>

      {/* Summary Cards */}
      <section style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px', marginBottom: '30px' }}>
        <StatCard 
          title="Active Loans" 
          value={overview?.totalActiveLoans || 0} 
        />
        <StatCard 
          title="Outstanding Balance" 
          value={`$${overview?.totalOutstandingBalance?.toFixed(2) || '0.00'}`} 
        />
        <StatCard 
          title="Total Paid" 
          value={`$${overview?.totalPaidAmount?.toFixed(2) || '0.00'}`} 
        />
      </section>

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '30px' }}>
        
        <section>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '10px', marginBottom: '15px' }}>
            <h3 style={{ margin: 0 }}>Your Active Loans</h3>
            <button 
              onClick={() => navigate('/customer/loans')}
              style={{ background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', fontSize: '14px', fontWeight: 'bold', padding: 0 }}>
              View All &rarr;
            </button>
          </div>
          {loans.length === 0 ? (
            <p style={{ color: '#666' }}>You have no active loans.</p>
          ) : (
            loans.map(loan => (
              <div key={loan.id} style={{ display: 'flex', flexDirection: 'column' }}>
                <div style={{ flexGrow: 1 }}>
                  <LoanSummaryCard loan={loan} />
                </div>
                <button 
                  onClick={() => navigate(`/customer/loans/${loan.id}`)}
                  style={{ marginTop: '-15px', marginBottom: '10px', padding: '8px', backgroundColor: '#f8f9fa', border: '1px solid #e0e0e0', borderTop: 'none', borderRadius: '0 0 8px 8px', cursor: 'pointer', fontWeight: 'bold' }}>
                  View Details
                </button>
              </div>
            ))
          )}
        </section>

        {/* Applications */}
        <section>
          <h3 style={{ borderBottom: '1px solid #eee', paddingBottom: '10px', marginBottom: '15px' }}>Recent Applications</h3>
          {applications.length === 0 ? (
            <p style={{ color: '#666' }}>You have no recent loan applications.</p>
          ) : (
            applications.map(app => (
              <ApplicationStatusCard key={app.id} application={app} />
            ))
          )}
        </section>
      </div>
      
      {/* Quick Actions */}
      <section style={{ marginTop: '40px' }}>
        <h3 style={{ borderBottom: '1px solid #eee', paddingBottom: '10px', marginBottom: '15px' }}>Quick Actions</h3>
        <div style={{ display: 'flex', gap: '15px', flexWrap: 'wrap' }}>
          <button 
            onClick={() => navigate('/customer/loan-products')}
            style={{ padding: '10px 20px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
            Apply for a Loan
          </button>
          <button 
            onClick={() => navigate('/customer/loan-products')}
            style={{ padding: '10px 20px', backgroundColor: '#f8f9fa', color: '#333', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
            Browse Loan Products
          </button>
        </div>
      </section>

    </div>
  );
};

export default CustomerDashboard;
