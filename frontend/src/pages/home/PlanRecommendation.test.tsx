import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { PlanRecommendation } from './PlanRecommendation';

describe('PlanRecommendation', () => {
  it('使用 Ant Design 按钮和公共 SVG 图标展示方案', () => {
    const { container } = render(<PlanRecommendation onBack={vi.fn()} />);

    expect(screen.getByRole('button', { name: '返回首页' })).toHaveClass('ant-btn');
    expect(screen.getByRole('button', { name: /换一批/ })).toHaveClass('ant-btn');
    expect(container.querySelector('.plan-cinema-name svg')).toBeInTheDocument();
    expect(container.querySelectorAll('.pref-item svg')).toHaveLength(5);
    expect(container.textContent).not.toMatch(/[🔄🏛️🎯📍💰🕒👥]/u);
  });
});
