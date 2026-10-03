import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { customerApi } from '../../api';
import Loading from '../../components/Loading';
import ErrorMessage from '../../components/ErrorMessage';

const CustomerProfile = () => {
  const navigate = useNavigate();

  const [profile, setProfile] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  
  const [isEditing, setIsEditing] = useState(false);
  const [formData, setFormData] = useState({
    firstName: '',
    lastName: '',
    phone: ''
  });
  const [saveLoading, setSaveLoading] = useState(false);
  const [saveError, setSaveError] = useState(null);
  const [saveSuccess, setSaveSuccess] = useState(false);

  useEffect(() => {
    const fetchProfile = async () => {
      try {
        setLoading(true);
        const res = await customerApi.getCustomerProfile();
        setProfile(res.data);
        setFormData({
          firstName: res.data.firstName || '',
          lastName: res.data.lastName || '',
          phone: res.data.phone || ''
        });
      } catch (err) {
        console.error(err);
        setError('Failed to load profile.');
      } finally {
        setLoading(false);
      }
    };
    fetchProfile();
  }, []);

  const handleChange = (e) => {
    setFormData({
      ...formData,
      [e.target.name]: e.target.value
    });
  };

  const handleSave = async (e) => {
    e.preventDefault();
    setSaveError(null);
    setSaveSuccess(false);
    try {
      setSaveLoading(true);
      const res = await customerApi.updateCustomerProfile(formData);
      setProfile(res.data);
      setIsEditing(false);
      setSaveSuccess(true);
    } catch (err) {
      console.error(err);
      setSaveError(err.response?.data?.message || 'Failed to update profile.');
    } finally {
      setSaveLoading(false);
    }
  };

  if (loading) return <Loading />;
  if (error) return <ErrorMessage message={error} />;
  if (!profile) return <ErrorMessage message="Profile not found" />;

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto', color: '#333' }}>
      <button onClick={() => navigate(-1)} style={{ marginBottom: '20px', background: 'none', border: 'none', color: '#0056b3', cursor: 'pointer', padding: 0, fontWeight: 'bold' }}>
        &larr; Back
      </button>

      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '20px' }}>
        <h2 style={{ margin: 0 }}>My Profile</h2>
        {!isEditing && (
          <button 
            onClick={() => { setIsEditing(true); setSaveSuccess(false); setSaveError(null); }}
            style={{ padding: '8px 16px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
            Edit Profile
          </button>
        )}
      </div>

      {saveSuccess && (
        <div style={{ padding: '15px', backgroundColor: '#d4edda', color: '#155724', borderRadius: '4px', marginBottom: '20px' }}>
          Profile updated successfully!
        </div>
      )}

      {saveError && <ErrorMessage message={saveError} />}

      <div style={{ backgroundColor: '#fff', padding: '30px', borderRadius: '8px', border: '1px solid #ddd' }}>
        {!isEditing ? (
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '25px' }}>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>First Name</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.firstName || 'Not provided'}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>Last Name</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.lastName || 'Not provided'}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>Email Address</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.email}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>Phone Number</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.phone || 'Not provided'}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>Date of Birth</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.dob ? new Date(profile.dob).toLocaleDateString() : 'Not provided'}</div>
            </div>
            <div>
              <div style={{ color: '#666', fontSize: '14px', marginBottom: '5px' }}>Customer ID</div>
              <div style={{ fontWeight: '500', fontSize: '16px' }}>{profile.id}</div>
            </div>
          </div>
        ) : (
          <form onSubmit={handleSave}>
            <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '20px', marginBottom: '20px' }}>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', color: '#666' }}>First Name *</label>
                <input 
                  type="text" 
                  name="firstName" 
                  value={formData.firstName} 
                  onChange={handleChange} 
                  required 
                  maxLength={100}
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', color: '#666' }}>Last Name *</label>
                <input 
                  type="text" 
                  name="lastName" 
                  value={formData.lastName} 
                  onChange={handleChange} 
                  required 
                  maxLength={100}
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', color: '#666' }}>Email Address (Read-only)</label>
                <input 
                  type="text" 
                  value={profile.email} 
                  disabled 
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', backgroundColor: '#f5f5f5', color: '#666', boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', color: '#666' }}>Phone Number *</label>
                <input 
                  type="text" 
                  name="phone" 
                  value={formData.phone} 
                  onChange={handleChange} 
                  required 
                  maxLength={20}
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', boxSizing: 'border-box' }}
                />
              </div>
              <div>
                <label style={{ display: 'block', marginBottom: '5px', color: '#666' }}>Date of Birth (Read-only)</label>
                <input 
                  type="text" 
                  value={profile.dob ? new Date(profile.dob).toLocaleDateString() : 'N/A'} 
                  disabled 
                  style={{ width: '100%', padding: '10px', borderRadius: '4px', border: '1px solid #ccc', backgroundColor: '#f5f5f5', color: '#666', boxSizing: 'border-box' }}
                />
              </div>
            </div>
            
            <div style={{ display: 'flex', gap: '10px' }}>
              <button 
                type="submit" 
                disabled={saveLoading}
                style={{ padding: '10px 20px', backgroundColor: '#0056b3', color: '#fff', border: 'none', borderRadius: '4px', cursor: 'pointer', fontWeight: 'bold' }}>
                {saveLoading ? 'Saving...' : 'Save Changes'}
              </button>
              <button 
                type="button" 
                onClick={() => { setIsEditing(false); setSaveError(null); }}
                disabled={saveLoading}
                style={{ padding: '10px 20px', backgroundColor: '#f8f9fa', color: '#333', border: '1px solid #ccc', borderRadius: '4px', cursor: 'pointer' }}>
                Cancel
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
};

export default CustomerProfile;
