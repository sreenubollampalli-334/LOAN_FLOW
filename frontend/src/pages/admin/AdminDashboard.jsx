import React from 'react';

const AdminDashboard = () => {
  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <h2 style={{ marginBottom: '20px' }}>System Admin Dashboard</h2>
      
      <div style={{ backgroundColor: '#fff', padding: '20px', borderRadius: '8px', border: '1px solid #ddd', marginBottom: '20px' }}>
        <h3 style={{ marginTop: 0, borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Global Statistics</h3>
        
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px', marginTop: '20px' }}>
          <div style={{ padding: '15px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee', textAlign: 'center' }}>
            <div style={{ fontSize: '14px', color: '#666', marginBottom: '10px' }}>Total Disbursements</div>
            <div style={{ fontSize: '24px', fontWeight: 'bold' }}>--</div>
            <div style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>Not available from backend</div>
          </div>
          
          <div style={{ padding: '15px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee', textAlign: 'center' }}>
            <div style={{ fontSize: '14px', color: '#666', marginBottom: '10px' }}>Total Amount Disbursed</div>
            <div style={{ fontSize: '24px', fontWeight: 'bold' }}>--</div>
            <div style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>Not available from backend</div>
          </div>

          <div style={{ padding: '15px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee', textAlign: 'center' }}>
            <div style={{ fontSize: '14px', color: '#666', marginBottom: '10px' }}>Failed Attempts</div>
            <div style={{ fontSize: '24px', fontWeight: 'bold' }}>--</div>
            <div style={{ fontSize: '12px', color: '#999', marginTop: '10px' }}>Not available from backend</div>
          </div>
        </div>
      </div>
      
      <div style={{ backgroundColor: '#fff3cd', padding: '15px', borderRadius: '8px', border: '1px solid #ffeeba', color: '#856404' }}>
        <strong>Note:</strong> Comprehensive global analytics are not currently supported by the backend API. Please navigate to the Disbursements tab to manage the queue.
      </div>
    </div>
  );
};

export default AdminDashboard;
