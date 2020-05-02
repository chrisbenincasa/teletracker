import React, { useContext } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { NetworkType } from '../../types';
import { FilterContext } from './FilterContext';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '100%',
      flexWrap: 'nowrap',
    },
    filterLabel: {
      paddingBottom: theme.spacing(0.5),
    },
    networkContainer: {
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
    },
    networkIcon: {
      width: 20,
      height: 20,
      borderRadius: theme.custom.borderRadius.circle,
    },
    selectedChip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
      backgroundColor: theme.palette.primary.main,
    },
    unselectedChip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
      backgroundColor: theme.palette.grey[700],
    },
  }),
);

interface Props {
  handleChange: (type?: NetworkType[]) => void;
  showTitle?: boolean;
}

export default function NetworkSelect(props: Props) {
  const classes = useStyles();
  const {
    filters: { networks },
  } = useContext(FilterContext);
  const isNetflixSelected =
    (networks && networks.includes('netflix')) ||
    (networks && networks.includes('netflix-kids'));
  const isHuluSelected = networks && networks.includes('hulu');
  const isHboSelected =
    (networks && networks.includes('hbo-go')) ||
    (networks && networks.includes('hbo-now'));

  const updateNetworks = (param: string, value?: NetworkType[]) => {
    props.handleChange(value);
  };

  return (
    <div className={classes.networkContainer}>
      {props.showTitle && (
        <Typography className={classes.filterLabel} display="block">
          Network
        </Typography>
      )}
      <div className={classes.chipContainer}>
        <Chip
          key={'all'}
          onClick={() => updateNetworks('networks', undefined)}
          size="medium"
          color={!networks ? 'primary' : 'secondary'}
          label="All"
          className={classes.chip}
        />
        <Chip
          key={'netflix'}
          onClick={() =>
            updateNetworks('networks', ['netflix', 'netflix-kids'])
          }
          size="medium"
          color={isNetflixSelected ? 'primary' : 'secondary'}
          label="Netlflix"
          className={classes.chip}
          icon={
            <img
              className={classes.networkIcon}
              src={`/images/logos/netflix/icon.jpg`}
              alt="Netflix logo"
            />
          }
        />
        <Chip
          key={'hulu'}
          onClick={() => updateNetworks('networks', ['hulu'])}
          size="medium"
          color={isHuluSelected ? 'primary' : 'secondary'}
          label="Hulu"
          className={classes.chip}
          icon={
            <img
              className={classes.networkIcon}
              src={`/images/logos/hulu/icon.jpg`}
              alt="Hulu logo"
            />
          }
        />
        <Chip
          key={'hbo'}
          onClick={() => updateNetworks('networks', ['hbo-go', 'hbo-now'])}
          size="medium"
          color={isHboSelected ? 'primary' : 'secondary'}
          label="HBO"
          className={classes.chip}
          icon={
            <img
              className={classes.networkIcon}
              src={`/images/logos/hbo-now/icon.jpg`}
              alt="HBO logo"
            />
          }
        />
      </div>
    </div>
  );
}

NetworkSelect.defaultProps = {
  showTitle: true,
};
