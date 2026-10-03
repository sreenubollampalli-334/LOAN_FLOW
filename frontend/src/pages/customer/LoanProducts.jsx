import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const LoanProducts = () => {
  const [products, setProducts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const navigate = useNavigate();

  useEffect(() => {
    const fetchProducts = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getLoanProducts();
        setProducts(res.data.content || []);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch loan products.');
      } finally {
        setLoading(false);
      }
    };
    fetchProducts();
  }, []);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;

  return (
    <div style={{ maxWidth: '1200px', margin: '0 auto', color: '#333' }}>
      <h2 style={{ marginBottom: '20px' }}>Available Loan Products</h2>
      {products.length === 0 ? (
        <p>No loan products are available at the moment.</p>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
          {products.map(product => (
            <div key={product.id} style={{ padding: '20px', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#fff', display: 'flex', flexDirection: 'column' }}>
              <h3 style={{ marginTop: 0 }}>{product.name}</h3>
              <p style={{ color: '#666', height: '40px', overflow: 'hidden', textOverflow: 'ellipsis', display: '-webkit-box', WebkitLineClamp: 2, WebkitBoxOrient: 'vertical' }}>
                {product.description}
              </p>
              
              <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '20px', fontSize: '14px', flexGrow: 1 }}>
                <div>
                  <div style={{ color: '#666' }}>Limits</div>
                  <div style={{ fontWeight: '500' }}>${product.minAmount} - ${product.maxAmount}</div>
                </div>
                <div style={{ textAlign: 'right' }}>
                  <div style={{ color: '#666' }}>Rate</div>
                  <div style={{ fontWeight: '500' }}>{product.interestRate}%</div>
                </div>
              </div>
              
              <div style={{ display: 'flex', gap: '10px' }}>
                <button 
                  onClick={() => navigate(`/customer/loan-products/${product.id}`)}
                  style={{ flex: 1, padding: '10px', backgroundColor: '#f8f9fa', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold', color: '#333' }}>
                  View Details
                </button>
                <button 
                  onClick={() => navigate(`/customer/apply-loan/${product.id}`)}
                  style={{ flex: 1, padding: '10px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                  Apply Now
                </button>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};

export default LoanProducts;
