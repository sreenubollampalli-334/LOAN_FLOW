import React from 'react';
import { useNavigate } from 'react-router-dom';

const ApplicationStatusCard = ({ application }) => {
  const navigate = useNavigate();
  // Simple status color logic
  let bgColor = '#fef7e0';
  let color = '#b06000';
  
  if (application.status === 'APPROVED' || application.status === 'DISBURSED') {
    bgColor = '#e6f4ea';
    color = '#137333';
  } else if (application.status === 'REJECTED') {
    bgColor = '#fce8e6';
    color = '#c5221f';
  }

  return (
    <div style={{ padding: '15px', border: '1px solid #e0e0e0', borderRadius: '8px', backgroundColor: '#fff', marginBottom: '10px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '10px' }}>
        <h4 style={{ margin: 0, color: '#333' }}>App #{application.id} - {application.productName || 'Unknown Product'}</h4>
        <span style={{ padding: '4px 8px', borderRadius: '4px', fontSize: '12px', backgroundColor: bgColor, color: color, fontWeight: 'bold' }}>
          {application.status}
        </span>
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-end' }}>
        <div style={{ fontSize: '14px' }}>
          <div style={{ color: '#666' }}>Requested Amount</div>
          <div style={{ fontWeight: '500', color: '#333' }}>${application.requestedAmount?.toFixed(2) || '0.00'}</div>
        </div>
        <button 
          onClick={() => navigate(`/customer/applications/${application.id}`)}
          style={{ padding: '6px 12px', backgroundColor: '#f8f9fa', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', fontSize: '12px' }}>
          View Details
        </button>
      </div>
    </div>
  );
};

export default ApplicationStatusCard;
