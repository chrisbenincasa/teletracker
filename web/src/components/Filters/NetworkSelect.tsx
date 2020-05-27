import React, { useContext } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import { NetworkType, networkToColor } from '../../types';
import { FilterContext } from './FilterContext';
import _ from 'lodash';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      margin: theme.spacing(0.25),
      flexBasis: '48%',
      justifyContent: 'flex-start',
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '100%',
      flexWrap: 'wrap',
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
      width: 48,
      maxHeight: 24,
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
  readonly handleChange: (type?: NetworkType[]) => void;
  readonly showTitle?: boolean;
}

export default function NetworkSelect(props: Props) {
  const classes = useStyles();
  const {
    filters: { networks },
  } = useContext(FilterContext);
  const isDisneyPlusSelected = networks && networks.includes('disney-plus');
  const isHboMaxSelected = networks && networks.includes('hbo-max');
  const isAmazonVideoSelected = networks && networks.includes('amazon-video');
  const isNetflixSelected =
    (networks && networks.includes('netflix')) ||
    (networks && networks.includes('netflix-kids'));
  const isHuluSelected = networks && networks.includes('hulu');
  const isHboSelected =
    (networks && networks.includes('hbo-go')) ||
    (networks && networks.includes('hbo-now'));

  const selectedNetworks = networks || [];

  const updateNetworks = (param: string, value?: NetworkType) => {
    let newSelectedNetworks: NetworkType[];

    if (value) {
      // If network isn't filtered yet, add it to current filter
      if (!selectedNetworks.includes(value)) {
        newSelectedNetworks = [...selectedNetworks, value];
      } else {
        newSelectedNetworks = selectedNetworks.filter(
          networkId => networkId !== value,
        );
      }
    } else {
      // User selected 'All', reset genre filter
      newSelectedNetworks = [];
    }

    if (_.xor(selectedNetworks, newSelectedNetworks).length !== 0) {
      props.handleChange(newSelectedNetworks);
    }
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
          style={{ width: '100%', flexBasis: '100%', justifyContent: 'center' }}
          className={classes.chip}
        />
        <Chip
          key={'netflix'}
          onClick={() => updateNetworks('networks', 'netflix')}
          size="medium"
          color={isNetflixSelected ? 'primary' : 'secondary'}
          label="Netflix"
          className={classes.chip}
          icon={
            <div
              style={{
                padding: '3px 6px',
                backgroundColor: networkToColor['netflix'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/netflix/netflix-full.svg`}
                alt="Netflix logo"
              />
            </div>
          }
        />
        <Chip
          key={'hulu'}
          onClick={() => updateNetworks('networks', 'hulu')}
          size="medium"
          color={isHuluSelected ? 'primary' : 'secondary'}
          label="Hulu"
          className={classes.chip}
          icon={
            <div
              style={{
                backgroundColor: networkToColor['hulu'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/hulu/hulu-full.svg`}
                alt="Hulu logo"
              />
            </div>
          }
        />
        <Chip
          key={'hbo'}
          onClick={() => updateNetworks('networks', 'hbo-now')}
          size="medium"
          color={isHboSelected ? 'primary' : 'secondary'}
          label="HBO"
          className={classes.chip}
          icon={
            <div
              style={{
                padding: '1px 5px',
                backgroundColor: networkToColor['hbo-now'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/hbo/hbo-full.svg`}
                alt="HBO logo"
              />
            </div>
          }
        />
        <Chip
          key={'disney-plus'}
          onClick={() => updateNetworks('networks', 'disney-plus')}
          size="medium"
          color={isDisneyPlusSelected ? 'primary' : 'secondary'}
          label="Disney Plus"
          className={classes.chip}
          icon={
            <div
              style={{
                padding: '1px 5px',
                backgroundColor: networkToColor['disney-plus'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/disney-plus/disney-plus-full.svg`}
                alt="Disney Plus logo"
              />
            </div>
          }
        />
        <Chip
          key={'hbo-max'}
          onClick={() => updateNetworks('networks', 'hbo-max')}
          size="medium"
          color={isHboMaxSelected ? 'primary' : 'secondary'}
          label="HBO Max"
          className={classes.chip}
          icon={
            <div
              style={{
                padding: '1px 5px',
                backgroundColor: networkToColor['hbo-max'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/hbo-max/hbo-max-full.svg`}
                alt="HBO Max logo"
              />
            </div>
          }
        />
        <Chip
          key={'amazon-video'}
          onClick={() => updateNetworks('networks', 'amazon-video')}
          size="medium"
          color={isAmazonVideoSelected ? 'primary' : 'secondary'}
          label="Amazon Video"
          className={classes.chip}
          icon={
            <div
              style={{
                padding: '1px 5px',
                backgroundColor: networkToColor['amazon-video'],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={`/images/logos/amazon-video/amazon-video-full.svg`}
                alt="Amazon Video logo"
              />
            </div>
          }
        />
      </div>
    </div>
  );
}

NetworkSelect.defaultProps = {
  showTitle: true,
};
