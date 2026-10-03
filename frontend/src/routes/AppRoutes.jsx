import React from 'react';
import { Routes, Route } from 'react-router-dom';
import Login from '../pages/Login';
import NotFound from '../pages/NotFound';
import Unauthorized from '../pages/Unauthorized';
import ProtectedRoute from '../components/ProtectedRoute';
import DashboardLayout from '../layouts/DashboardLayout';

// Dashboards
import CustomerDashboard from '../pages/customer/CustomerDashboard';
import LoanProducts from '../pages/customer/LoanProducts';
import LoanProductDetails from '../pages/customer/LoanProductDetails';
import ApplyLoan from '../pages/customer/ApplyLoan';
import MyApplications from '../pages/customer/MyApplications';
import ApplicationDetails from '../pages/customer/ApplicationDetails';
import MyLoans from '../pages/customer/MyLoans';
import LoanDetails from '../pages/customer/LoanDetails';
import CustomerProfile from '../pages/customer/CustomerProfile';
import OfficerDashboard from '../pages/officer/OfficerDashboard';
import OfficerApplications from '../pages/officer/OfficerApplications';
import OfficerApplicationReview from '../pages/officer/OfficerApplicationReview';
import ManagerDashboard from '../pages/manager/ManagerDashboard';
import ManagerApplications from '../pages/manager/ManagerApplications';
import ManagerApplicationDetails from '../pages/manager/ManagerApplicationDetails';
import AdminDashboard from '../pages/admin/AdminDashboard';
import AdminDisbursements from '../pages/admin/AdminDisbursements';
import AdminDisbursementDetails from '../pages/admin/AdminDisbursementDetails';

const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/unauthorized" element={<Unauthorized />} />
      
      {/* 
        The root route acts as a gateway that will redirect 
        to the appropriate dashboard within ProtectedRoute or Login 
      */}
      <Route 
        path="/" 
        element={
          <ProtectedRoute>
            <DashboardLayout />
          </ProtectedRoute>
        }
      >
        <Route index element={
          <div>
            <h3>Welcome to LoanFlow</h3>
            <p>Please select a specific module or re-login if you do not see your dashboard.</p>
          </div>
        } />
        
        {/* Customer Routes */}
        <Route path="customer">
          <Route path="dashboard" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <CustomerDashboard />
            </ProtectedRoute>
          } />
          <Route path="loan-products" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <LoanProducts />
            </ProtectedRoute>
          } />
          <Route path="loan-products/:id" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <LoanProductDetails />
            </ProtectedRoute>
          } />
          <Route path="apply-loan/:productId" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <ApplyLoan />
            </ProtectedRoute>
          } />
          <Route path="applications" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <MyApplications />
            </ProtectedRoute>
          } />
          <Route path="applications/:id" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <ApplicationDetails />
            </ProtectedRoute>
          } />
          <Route path="loans" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <MyLoans />
            </ProtectedRoute>
          } />
          <Route path="loans/:id" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <LoanDetails />
            </ProtectedRoute>
          } />
          <Route path="profile" element={
            <ProtectedRoute allowedRoles={['CUSTOMER']}>
              <CustomerProfile />
            </ProtectedRoute>
          } />
        </Route>

        {/* Officer Routes */}
        <Route path="officer">
          <Route path="dashboard" element={
            <ProtectedRoute allowedRoles={['CREDIT_OFFICER']}>
              <OfficerDashboard />
            </ProtectedRoute>
          } />
          <Route path="applications" element={
            <ProtectedRoute allowedRoles={['CREDIT_OFFICER']}>
              <OfficerApplications />
            </ProtectedRoute>
          } />
          <Route path="applications/:id" element={
            <ProtectedRoute allowedRoles={['CREDIT_OFFICER']}>
              <OfficerApplicationReview />
            </ProtectedRoute>
          } />
        </Route>

        {/* Manager Routes */}
        <Route path="manager">
          <Route path="dashboard" element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <ManagerDashboard />
            </ProtectedRoute>
          } />
          <Route path="applications" element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <ManagerApplications />
            </ProtectedRoute>
          } />
          <Route path="applications/:id" element={
            <ProtectedRoute allowedRoles={['MANAGER']}>
              <ManagerApplicationDetails />
            </ProtectedRoute>
          } />
        </Route>

        {/* Admin Routes */}
        <Route path="admin">
          <Route path="dashboard" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminDashboard />
            </ProtectedRoute>
          } />
          <Route path="disbursements" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminDisbursements />
            </ProtectedRoute>
          } />
          <Route path="disbursements/:id" element={
            <ProtectedRoute allowedRoles={['ADMIN']}>
              <AdminDisbursementDetails />
            </ProtectedRoute>
          } />
        </Route>

      </Route>

      <Route path="*" element={<NotFound />} />
    </Routes>
  );
};

export default AppRoutes;
