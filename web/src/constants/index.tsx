import { GridProps, GridSize } from '@material-ui/core/Grid';
import { Breakpoint } from '@material-ui/core/styles/createBreakpoints';

export const GA_TRACKING_ID: string = 'UA-123012032-1';

const ITEM_SIZE_IN_GRID: Record<Breakpoint, GridSize> = {
  xs: 6,
  sm: 6,
  md: 3,
  lg: 2,
  xl: 2,
};

export const GRID_ITEM_SIZE_IN_COLUMNS: Partial<GridProps> = {
  ...ITEM_SIZE_IN_GRID,
};

export function itemsPerRow(width: Breakpoint) {
  let itemSize = ITEM_SIZE_IN_GRID[width];
  if (itemSize !== 'auto') {
    return TOTAL_COLUMNS / itemSize;
  }
}

// Material Design’s responsive UI is based on a 12-column grid layout.
export const TOTAL_COLUMNS: number = 12;

export const BASE_IMAGE_URL = 'https://image.tmdb.org/t/p/';

export const GOOGLE_ACCOUNT_MERGE = 'GOOGLE_ACCOUNT_MERGE';

export const DEFAULT_POPULAR_LIMIT = 18;

export const DEFAULT_ROWS: number = 3;
