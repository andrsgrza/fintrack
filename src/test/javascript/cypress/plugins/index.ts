// ***********************************************************
// This example plugins/index.js can be used to load plugins
//
// You can change the location of this file or turn off loading
// the plugins file with the 'pluginsFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/plugins-guide
// ***********************************************************

// This function is called when a project is opened or re-opened (e.g. due to
// the project's config changing)
import { existsSync, mkdirSync, writeFileSync } from 'fs';
import { lighthouse, pa11y, prepareAudit } from 'cypress-audit';

const delay = (milliseconds: number) => new Promise(resolve => setTimeout(resolve, milliseconds));

type NotificationPollingInput = {
  baseUrl: string;
  bearerToken: string;
  expectedCount: number;
  timeoutMs: number;
};

type NotificationLogicalKeysPollingInput = {
  baseUrl: string;
  bearerToken: string;
  expectedLogicalEventKeys: string[];
  timeoutMs: number;
};

type NotificationWithLogicalEventKey = { snapshot: { logicalEventKey: string } };

const isNotificationPollingInput = (value: unknown): value is NotificationPollingInput => {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as Partial<NotificationPollingInput>;
  return (
    typeof candidate.baseUrl === 'string' &&
    typeof candidate.bearerToken === 'string' &&
    Number.isInteger(candidate.expectedCount) &&
    candidate.expectedCount > 0 &&
    Number.isInteger(candidate.timeoutMs) &&
    candidate.timeoutMs > 0
  );
};

const isNotificationLogicalKeysPollingInput = (value: unknown): value is NotificationLogicalKeysPollingInput => {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as Partial<NotificationLogicalKeysPollingInput>;
  return (
    typeof candidate.baseUrl === 'string' &&
    typeof candidate.bearerToken === 'string' &&
    Array.isArray(candidate.expectedLogicalEventKeys) &&
    candidate.expectedLogicalEventKeys.length > 0 &&
    candidate.expectedLogicalEventKeys.every(key => typeof key === 'string' && key.length > 0) &&
    Number.isInteger(candidate.timeoutMs) &&
    candidate.timeoutMs > 0
  );
};

const hasLogicalEventKey = (value: unknown): value is NotificationWithLogicalEventKey => {
  if (value === null || typeof value !== 'object') return false;
  const candidate = value as { snapshot?: unknown };
  if (candidate.snapshot === null || typeof candidate.snapshot !== 'object') return false;
  const snapshot = candidate.snapshot as { logicalEventKey?: unknown };
  return typeof snapshot.logicalEventKey === 'string';
};

export default (on: Cypress.PluginEvents, config: Cypress.PluginConfigOptions) => {
  on('before:browser:launch', (browser, launchOptions) => {
    prepareAudit(launchOptions);
    if (browser.name === 'chrome' && browser.isHeadless) {
      launchOptions.args.push('--disable-gpu');
      return launchOptions;
    }
  });

  // Allows logging with cy.task('log', 'message') or cy.task('table', object)
  on('task', {
    log(message) {
      console.log(message);
      return null;
    },
    table(message) {
      console.table(message);
      return null;
    },
    async waitForNotifications(input: unknown) {
      if (!isNotificationPollingInput(input)) throw new Error('Invalid notification polling input.');
      const endpoint = new URL('/api/notifications?page=0&size=100', input.baseUrl);
      const deadline = Date.now() + input.timeoutMs;
      do {
        const response = await fetch(endpoint, { headers: { Authorization: `Bearer ${input.bearerToken}` } });
        if (!response.ok) throw new Error(`Notification polling returned HTTP ${response.status}.`);
        const body: unknown = await response.json();
        if (
          body !== null &&
          typeof body === 'object' &&
          'totalElements' in body &&
          typeof body.totalElements === 'number' &&
          body.totalElements >= input.expectedCount
        ) {
          return true;
        }
        await delay(100);
      } while (Date.now() < deadline);
      throw new Error(`Timed out waiting for ${input.expectedCount} materialized notifications.`);
    },
    async waitForNotificationLogicalKeys(input: unknown) {
      if (!isNotificationLogicalKeysPollingInput(input)) throw new Error('Invalid notification logical-key polling input.');
      const endpoint = new URL('/api/notifications?page=0&size=100', input.baseUrl);
      const deadline = Date.now() + input.timeoutMs;
      do {
        const response = await fetch(endpoint, { headers: { Authorization: `Bearer ${input.bearerToken}` } });
        if (!response.ok) throw new Error(`Notification polling returned HTTP ${response.status}.`);
        const body: unknown = await response.json();
        if (body !== null && typeof body === 'object' && 'notifications' in body && Array.isArray(body.notifications)) {
          const logicalEventKeys = new Set<string>();
          body.notifications.forEach((notification: unknown) => {
            if (hasLogicalEventKey(notification)) logicalEventKeys.add(notification.snapshot.logicalEventKey);
          });
          if (input.expectedLogicalEventKeys.every(key => logicalEventKeys.has(key))) return true;
        }
        await delay(100);
      } while (Date.now() < deadline);
      throw new Error(`Timed out waiting for notification keys: ${input.expectedLogicalEventKeys.join(', ')}.`);
    },
  });

  on('task', {
    lighthouse: lighthouse(async lighthouseReport => {
      const { default: ReportGenerator } = await import('lighthouse/report/generator/report-generator');
      if (!existsSync('target/cypress/')) {
        mkdirSync('target/cypress/', { recursive: true });
      }
      writeFileSync('target/cypress/lhreport.html', ReportGenerator.generateReport(lighthouseReport.lhr, 'html'));
    }),
    pa11y: pa11y(),
  });
  return config;
};
