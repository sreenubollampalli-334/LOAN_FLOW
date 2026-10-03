import React, { useState, useEffect } from 'react';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';
import ApplicationStatusCard from '../../components/customer/ApplicationStatusCard';

const MyApplications = () => {
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  // Basic pagination state (could be expanded later with controls)
  const [page] = useState(0);
  const [size] = useState(20);

  useEffect(() => {
    const fetchApplications = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getMyApplications(page, size);
        setApplications(res.data.content || []);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch applications.');
      } finally {
        setLoading(false);
      }
    };
    fetchApplications();
  }, [page, size]);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1000px', margin: '0 auto', color: '#333' }}>
      <h2 style={{ marginBottom: '20px' }}>My Applications</h2>
      
      {applications.length === 0 ? (
        <div style={{ padding: '40px', textAlign: 'center', backgroundColor: '#f8f9fa', borderRadius: '8px', border: '1px solid #eee' }}>
          <p style={{ fontSize: '16px', color: '#666' }}>You have not submitted any loan applications yet.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '15px' }}>
          {applications.map(app => (
            <ApplicationStatusCard key={app.id} application={app} />
          ))}
        </div>
      )}
      
    </div>
  );
};

export default MyApplications;
