import type { ProfileTagPolarity, ProfileTagStatus, ProfileTagType } from './api';

export const PROFILE_TAG_TYPE_OPTIONS: ReadonlyArray<{ label: string; value: ProfileTagType }> = [
  { label: '电影类型', value: 'MOVIE_GENRE' },
  { label: '观影时段', value: 'TIME' },
  { label: '影院', value: 'CINEMA' },
  { label: '影厅', value: 'HALL' },
  { label: '价格区间', value: 'PRICE' },
  { label: '座位偏好', value: 'SEAT' },
];

export const PROFILE_TAG_VALUE_OPTIONS: Record<
  Exclude<ProfileTagType, 'CINEMA' | 'HALL'>,
  ReadonlyArray<{ label: string; value: string }>
> = {
  MOVIE_GENRE: [
    { label: '动作', value: '动作' },
    { label: '喜剧', value: '喜剧' },
    { label: '爱情', value: '爱情' },
    { label: '科幻', value: '科幻' },
    { label: '动画', value: '动画' },
    { label: '悬疑', value: '悬疑' },
    { label: '剧情', value: '剧情' },
  ],
  TIME: [
    { label: '上午', value: '上午' },
    { label: '下午', value: '下午' },
    { label: '晚上', value: '晚上' },
  ],
  PRICE: [
    { label: '¥30 以下', value: '¥30 以下' },
    { label: '¥30-50', value: '¥30-50' },
    { label: '¥50 以上', value: '¥50 以上' },
  ],
  SEAT: [
    { label: '中间位置', value: '中间位置' },
    { label: '靠前位置', value: '靠前位置' },
    { label: '靠后位置', value: '靠后位置' },
  ],
};

export const PROFILE_TAG_POLARITY_OPTIONS: ReadonlyArray<{
  label: string;
  value: ProfileTagPolarity;
}> = [
  { label: '喜欢', value: 'LIKE' },
  { label: '不喜欢', value: 'DISLIKE' },
];

export const PROFILE_TAG_TYPE_LABELS: Record<ProfileTagType, string> = Object.fromEntries(
  PROFILE_TAG_TYPE_OPTIONS.map((option) => [option.value, option.label]),
) as Record<ProfileTagType, string>;

export const PROFILE_TAG_STATUS_LABELS: Record<ProfileTagStatus, string> = {
  ACTIVE: '启用中',
  DISABLED: '已停用',
  EXPIRED: '已过期',
  DELETED: '已删除',
};
