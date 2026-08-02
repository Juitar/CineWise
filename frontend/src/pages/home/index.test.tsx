import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import HomePage from './index';

describe('HomePage', () => {
  it('提供唯一的页面主标题', () => {
    render(<HomePage />);

    expect(screen.getByRole('heading', { level: 1, name: '妙语购票' })).toBeInTheDocument();
  });
});
