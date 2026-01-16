import { test as base, expect } from '@playwright/test';

// Test fixtures for authentication and common setup
export interface TestFixtures {
  authenticatedPage: void;
}

export const test = base.extend<TestFixtures>({
  // Authenticated page fixture - logs in before test
  authenticatedPage: async ({ page }, use) => {
    // This fixture would be used when you need an authenticated session
    // For now, it's a placeholder that can be extended when testing real auth
    await use();
  },
});

export { expect };

// Test data constants
export const TEST_DATA = {
  // Database connection test data (mock values)
  mysql: {
    host: 'localhost',
    port: 3306,
    username: 'testuser',
    password: 'testpass',
  },
  postgresql: {
    host: 'localhost',
    port: 5432,
    username: 'testuser',
    password: 'testpass',
    database: 'testdb',
  },
  oracle: {
    host: 'localhost',
    port: 1521,
    username: 'testuser',
    password: 'testpass',
    database: 'ORCL',
  },
};

// Page object models for common pages
export class LoginPage {
  constructor(private page: ReturnType<typeof base['use']>['page']) {}

  async goto() {
    await this.page.goto('/login');
  }

  async fillCredentials(
    host: string,
    port: number,
    username: string,
    password: string,
    dbType: 'mysql' | 'postgresql' | 'oracle' = 'mysql'
  ) {
    await this.page.fill('[data-testid="host-input"]', host);
    await this.page.fill('[data-testid="port-input"]', port.toString());
    await this.page.fill('[data-testid="username-input"]', username);
    await this.page.fill('[data-testid="password-input"]', password);
    await this.page.selectOption('[data-testid="dbtype-select"]', dbType);
  }

  async submit() {
    await this.page.click('[data-testid="login-button"]');
  }

  async expectError(message: string) {
    await expect(this.page.locator('[data-testid="error-message"]')).toContainText(message);
  }

  async expectSuccess() {
    // After successful login, should redirect to dashboard or main page
    await expect(this.page).toHaveURL(/\/(dashboard|accounts)/);
  }
}

// Dashboard page helper
export class DashboardPage {
  constructor(private page: ReturnType<typeof base['use']>['page']) {}

  async goto() {
    await this.page.goto('/dashboard');
  }

  async expectStatsVisible() {
    await expect(this.page.locator('[data-testid="stats-card"]').first()).toBeVisible();
  }
}
