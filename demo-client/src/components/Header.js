import React from 'react';

export const Header = ({ title, webId, onLogout, onBack }) => (
  <header className="app-header">
    {onBack ? (
      <button className="back-button" onClick={onBack}>← Back</button>
    ) : (
      <h1>{title}</h1>
    )}
    {!onBack && <h1>{title}</h1>}
    {webId && (
      <div className="user-info">
        <span title={webId}>{webId?.split('/').pop()?.replace('#me', '') || 'User'}</span>
        <button onClick={onLogout} className="logout-button">Logout</button>
      </div>
    )}
  </header>
);

export default Header;
