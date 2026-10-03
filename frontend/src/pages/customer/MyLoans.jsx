import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import LoanSummaryCard from '../../components/customer/LoanSummaryCard';

const MyLoans = () => {
  const [loans, setLoans] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const [page] = useState(0);
  const [size] = useState(20);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchLoans = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getMyLoans(page, size);
        setLoans(res.data.content || []);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch your loans.');
      } finally {
        setLoading(false);
      }
    };
    fetchLoans();
  }, [page, size]);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', color: '#333' }}>
      <h2 style={{ marginBottom: '20px' }}>My Loans</h2>
      
      {loans.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', backgroundColor: '#f8f9fa', borderRadius: '8px', border: '1px solid #eee' }}>
          <p style={{ fontSize: '16px', color: '#666' }}>You have no active loans.</p>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          {loans.map(loan => (
            <div key={loan.id} style={{ display: 'flex', flexDirection: 'column' }}>
              <div style={{ flexGrow: 1 }}>
                <LoanSummaryCard loan={loan} />
              </div>
              <button 
                onClick={() => navigate(`/customer/loans/${loan.id}`)}
                style={{ padding: '10px', backgroundColor: '#f8f9fa', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                View Details
              </button>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default MyLoans;
