import React from 'react';

const StatCard = ({ title, value, subtitle }) => (
  <div style={{ padding: '20px', border: '1px solid #e0e0e0', borderRadius: '8px', backgroundColor: '#fff', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }}>
    <h3 style={{ margin: '0 0 10px 0', fontSize: '14px', color: '#666' }}>{title}</h3>
    <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#333' }}>{value}</div>
    {subtitle && <div style={{ fontSize: '12px', color: '#999', marginTop: '8px' }}>{subtitle}</div>}
  </div>
);

export default StatCard;
