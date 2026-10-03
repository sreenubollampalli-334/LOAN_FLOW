import React, { useState, useEffect } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const LoanProductDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [product, setProduct] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    const fetchProduct = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getLoanProductById(id);
        setProduct(res.data);
      } catch (err) {
        console.error(err);
        setError('Failed to fetch product details.');
      } finally {
        setLoading(false);
      }
    };
    fetchProduct();
  }, [id]);

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!product) return <ErrorMessage message="Product not found" />;

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', backgroundColor: '#fff', padding: '30px', borderRadius: '8px', border: '1px solid #ddd', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold' }}>
        &larr; Back to Products
      </button>
      
      <h2 style={{ marginTop: 0, fontSize: '28px' }}>{product.name}</h2>
      <p style={{ fontSize: '16px', color: '#555', lineHeight: '1.6' }}>{product.description}</p>
      
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '20px', marginTop: '30px', padding: '20px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee' }}>
        <div>
          <h4 style={{ margin: '0 0 10px 0', color: '#333' }}>Loan Amount</h4>
          <div><strong>Min:</strong> ${product.minAmount}</div>
          <div><strong>Max:</strong> ${product.maxAmount}</div>
        </div>
        <div>
          <h4 style={{ margin: '0 0 10px 0', color: '#333' }}>Tenure (Months)</h4>
          <div><strong>Min:</strong> {product.minTermMonths} months</div>
          <div><strong>Max:</strong> {product.maxTermMonths} months</div>
        </div>
        <div>
          <h4 style={{ margin: '0 0 10px 0', color: '#333' }}>Interest Rate</h4>
          <div style={{ fontSize: '24px', fontWeight: 'bold', color: '#0056b3' }}>{product.interestRate}%</div>
        </div>
      </div>

      {product.requiredDocuments && product.requiredDocuments.length > 0 && (
        <div style={{ marginTop: '30px' }}>
          <h4 style={{ borderBottom: '1px solid #eee', paddingBottom: '10px' }}>Required Documents</h4>
          <ul style={{ paddingLeft: '20px', color: '#555' }}>
            {product.requiredDocuments.map((doc, idx) => (
              <li key={idx} style={{ marginBottom: '8px' }}>
                {doc.documentType} {doc.isMandatory ? <strong style={{ color: '#c5221f' }}>(Mandatory)</strong> : <span style={{ color: '#888' }}>(Optional)</span>}
              </li>
            ))}
          </ul>
        </div>
      )}

      <div style={{ marginTop: '40px' }}>
        <button 
          onClick={() => navigate(`/customer/apply-loan/${product.id}`)}
          style={{ padding: '12px 24px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '16px', fontWeight: 'bold' }}>
          Apply for this Loan
        </button>
      </div>
    </div>
  );
};

export default LoanProductDetails;
