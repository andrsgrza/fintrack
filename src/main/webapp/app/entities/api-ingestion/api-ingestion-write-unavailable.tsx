import React from 'react';
import TechnicalWriteUnavailable from 'app/entities/technical-write-unavailable';

export const ApiIngestionWriteUnavailable = () => (
  <TechnicalWriteUnavailable
    backTo="/api-ingestion"
    dataCyPrefix="apiIngestion"
    technicalViewContentKey="fintrackApp.apiIngestion.technicalView"
    technicalViewDefault="Technical view — API ingestion product flow is deferred. This page is metadata/debug only."
    titleContentKey="fintrackApp.apiIngestion.writeUnavailableTitle"
    titleDefault="Api Ingestion technical view"
    messageContentKey="fintrackApp.apiIngestion.writeUnavailable"
    messageDefault="Api Ingestion create, edit, and delete are not product actions yet. API ingestion metadata is managed by future API ingestion workflow commands."
  />
);

export default ApiIngestionWriteUnavailable;
