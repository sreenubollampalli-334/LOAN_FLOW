import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const ApplicationDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const [application, setApplication] = useState(null);
  const [product, setProduct] = useState(null);
  const [documents, setDocuments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // Edit states
  const [amount, setAmount] = useState('');
  const [term, setTerm] = useState('');
  const [purpose, setPurpose] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState(null);
  const [uploadingDocType, setUploadingDocType] = useState(null);

  const fetchData = useCallback(async () => {
    try {
      setLoading(true);
      const appRes = await customerApi.getMyApplicationById(id);
      const app = appRes.data;
      setApplication(app);
      setAmount(app.requestedAmount?.toString() || '');
      setTerm(app.termMonths?.toString() || '');
      setPurpose(app.purpose || '');

      const [prodRes, docsRes] = await Promise.all([
        customerApi.getLoanProductById(app.productId),
        customerApi.getApplicationDocuments(id).catch(() => ({ data: [] }))
      ]);
      setProduct(prodRes.data);
      setDocuments(docsRes.data);
    } catch (err) {
      console.error(err);
      setError('Failed to fetch application details.');
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => {
    fetchData();
  }, [fetchData]);

  const handleUpdate = async () => {
    try {
      setSubmitting(true);
      setSubmitError(null);
      await customerApi.updateLoanApplication(id, {
        requestedAmount: parseFloat(amount),
        termMonths: parseInt(term, 10),
        purpose: purpose
      });
      await fetchData();
    } catch (err) {
      setSubmitError(err.response?.data?.message || 'Failed to update application');
    } finally {
      setSubmitting(false);
    }
  };

  const handleFileUpload = async (e, docType) => {
    const file = e.target.files[0];
    if (!file) return;
    try {
      setUploadingDocType(docType);
      setSubmitError(null);
      await customerApi.uploadDocument(id, docType, file);
      await fetchData();
    } catch (err) {
      setSubmitError(err.response?.data?.message || `Failed to upload ${docType}`);
    } finally {
      setUploadingDocType(null);
    }
  };

  const handleResubmit = async () => {
    try {
      setSubmitting(true);
      setSubmitError(null);
      await customerApi.submitLoanApplication(id);
      await fetchData();
    } catch (err) {
      setSubmitError(err.response?.data?.message || 'Failed to resubmit application');
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!application) return <ErrorMessage message="Application not found" />;

  let statusBg = '#fef7e0';
  let statusColor = '#b06000';
  if (application.status === 'APPROVED' || application.status === 'DISBURSED') {
    statusBg = '#e6f4ea';
    statusColor = '#137333';
  } else if (application.status === 'REJECTED') {
    statusBg = '#fce8e6';
    statusColor = '#c5221f';
  } else if (application.status === 'DRAFT') {
    statusBg = '#e8f0fe';
    statusColor = '#1a73e8';
  } else if (application.status === 'RETURNED_FOR_REVIEW') {
    statusBg = '#fff3cd';
    statusColor = '#856404';
  }

  const isEditable = application.status === 'DRAFT' || application.status === 'RETURNED_FOR_REVIEW';

  // Get most recent doc per type
  const docsByType = {};
  [...documents].sort((a, b) => new Date(b.uploadedAt) - new Date(a.uploadedAt))
    .forEach(doc => {
      if (!docsByType[doc.documentType]) docsByType[doc.documentType] = doc;
    });

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', backgroundColor: '#fff', padding: '30px', borderRadius: '8px', border: '1px solid #ddd', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold' }}>
        &larr; Back
      </button>
      
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderBottom: '1px solid #eee', paddingBottom: '15px', marginBottom: '20px' }}>
        <h2 style={{ margin: 0, fontSize: '24px' }}>Application #{application.id}</h2>
        <span style={{ padding: '6px 12px', borderRadius: '4px', fontSize: '14px', backgroundColor: statusBg, color: statusColor, fontWeight: 'bold' }}>
          {isEditable && application.updatedAt > application.createdAt ? (application.status === 'RETURNED_FOR_REVIEW' ? 'RETURNED FOR REVIEW' : 'ADDITIONAL INFORMATION REQUIRED') : application.status}
        </span>
      </div>

      {application.status === 'RETURNED_FOR_REVIEW' && (
        <div style={{ padding: '15px', backgroundColor: '#fff3cd', color: '#856404', borderRadius: '8px', border: '1px solid #ffeeba', marginBottom: '20px' }}>
          <strong>Attention Required:</strong> Your application was returned for review. Please update your details or documents and submit again.
        </div>
      )}

      <ErrorMessage message={submitError} />

      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
        <div style={{ padding: '20px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee' }}>
          <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Loan Details</h4>
          <div style={{ marginBottom: '10px' }}>
            <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Product</span>
            <strong style={{ fontSize: '16px' }}>{application.productName}</strong>
          </div>
          
          {isEditable ? (
            <>
              <div style={{ marginBottom: '10px' }}>
                <label style={{ color: '#666', display: 'block', fontSize: '14px' }}>Requested Amount</label>
                <input type="number" value={amount} onChange={e => setAmount(e.target.value)} style={{ width: '100%', padding: '8px' }} />
              </div>
              <div style={{ marginBottom: '10px' }}>
                <label style={{ color: '#666', display: 'block', fontSize: '14px' }}>Term (Months)</label>
                <input type="number" value={term} onChange={e => setTerm(e.target.value)} style={{ width: '100%', padding: '8px' }} />
              </div>
            </>
          ) : (
            <>
              <div style={{ marginBottom: '10px' }}>
                <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Requested Amount</span>
                <strong style={{ fontSize: '16px' }}>${application.requestedAmount?.toFixed(2)}</strong>
              </div>
              <div style={{ marginBottom: '10px' }}>
                <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Term (Months)</span>
                <strong style={{ fontSize: '16px' }}>{application.termMonths}</strong>
              </div>
            </>
          )}

          {application.approvedAmount && (
            <div style={{ marginBottom: '10px' }}>
              <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Approved Amount</span>
              <strong style={{ fontSize: '16px', color: '#137333' }}>${application.approvedAmount?.toFixed(2)}</strong>
            </div>
          )}
        </div>

        <div style={{ padding: '20px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee' }}>
          <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Additional Information</h4>
          
          {isEditable ? (
            <div style={{ marginBottom: '10px' }}>
              <label style={{ color: '#666', display: 'block', fontSize: '14px' }}>Purpose</label>
              <textarea value={purpose} onChange={e => setPurpose(e.target.value)} rows={3} style={{ width: '100%', padding: '8px' }} />
            </div>
          ) : (
            <div style={{ marginBottom: '10px' }}>
              <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Purpose</span>
              <span style={{ fontSize: '16px' }}>{application.purpose || 'Not specified'}</span>
            </div>
          )}
          
          <div style={{ marginBottom: '10px' }}>
            <span style={{ color: '#666', display: 'block', fontSize: '14px' }}>Last Updated</span>
            <span style={{ fontSize: '16px' }}>{new Date(application.updatedAt).toLocaleString()}</span>
          </div>

          {isEditable && (
            <button onClick={handleUpdate} disabled={submitting} style={{ marginTop: '10px', padding: '8px 16px', backgroundColor: '#6c757d', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer' }}>
              Save Draft Changes
            </button>
          )}
        </div>
      </div>

      {product && product.requiredDocuments && (
        <div style={{ marginTop: '20px', padding: '20px', backgroundColor: '#f9f9f9', borderRadius: '8px', border: '1px solid #eee' }}>
          <h4 style={{ margin: '0 0 15px 0', color: '#333' }}>Documents</h4>
          <table style={{ width: '100%', borderCollapse: 'collapse' }}>
            <thead>
              <tr style={{ borderBottom: '2px solid #ddd', textAlign: 'left' }}>
                <th style={{ padding: '10px' }}>Type</th>
                <th style={{ padding: '10px' }}>Status</th>
                {isEditable && <th style={{ padding: '10px' }}>Action</th>}
              </tr>
            </thead>
            <tbody>
              {product.requiredDocuments.map(reqDoc => {
                const uploaded = docsByType[reqDoc.documentType];
                return (
                  <tr key={reqDoc.documentType} style={{ borderBottom: '1px solid #eee' }}>
                    <td style={{ padding: '10px' }}>{reqDoc.documentType} {reqDoc.isMandatory ? '*' : ''}</td>
                    <td style={{ padding: '10px' }}>
                      {uploaded ? (
                        <span style={{ color: '#137333', fontWeight: 'bold' }}>Uploaded ({uploaded.originalFilename})</span>
                      ) : (
                        <span style={{ color: '#c5221f' }}>Missing</span>
                      )}
                    </td>
                    {isEditable && (
                      <td style={{ padding: '10px' }}>
                        <input 
                          type="file" 
                          onChange={(e) => handleFileUpload(e, reqDoc.documentType)}
                          disabled={uploadingDocType === reqDoc.documentType}
                        />
                        {uploadingDocType === reqDoc.documentType && <span style={{ marginLeft: '10px', fontSize: '12px' }}>Uploading...</span>}
                      </td>
                    )}
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}

      {isEditable && (
        <div style={{ marginTop: '20px', textAlign: 'right' }}>
          <button 
            onClick={handleResubmit} 
            disabled={submitting}
            style={{ padding: '12px 24px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontSize: '16px', fontWeight: 'bold' }}>
            {submitting ? 'Submitting...' : application.status === 'RETURNED_FOR_REVIEW' ? 'Submit Application Again' : 'Submit Application'}
          </button>
        </div>
      )}
    </div>
  );
};

export default ApplicationDetails;
