import { Theme } from '@material-ui/core';
import { CSSProperties } from '@material-ui/core/styles/withStyles';

export const layoutStyles: (theme: Theme) => CSSProperties = (
  theme: Theme,
) => ({
  width: 'auto',
  marginTop: theme.spacing.unit,
  marginLeft: theme.spacing.unit * 3,
  marginRight: theme.spacing.unit * 3,
  [theme.breakpoints.up(1100 + theme.spacing.unit * 3 * 2)]: {
    width: 1100,
    marginLeft: 'auto',
    marginRight: 'auto',
  },
});
