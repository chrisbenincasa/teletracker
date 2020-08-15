import React, { useContext, CSSProperties } from 'react';
import {
  Chip,
  createStyles,
  makeStyles,
  Theme,
  Typography,
} from '@material-ui/core';
import clsx from 'clsx';
import { networkToColor, networkToPrettyName, NetworkType } from '../../types';
import { FilterContext } from './FilterContext';
import { getLogoUrl } from '../../utils/image-helper';
import * as allNetworks from '../../constants/networks';
import {
  SelectableNetworks,
  selectableNetworksEqual,
} from '../../utils/searchFilters';
import FilterSectionTitle from './FilterSectionTitle';

const useStyles = makeStyles((theme: Theme) =>
  createStyles({
    chip: {
      // margin: theme.spacing(0.25),
      // flexBasis: '48%',
      justifyContent: 'flex-start',
      // flexGrow: 1,
    },
    chipContainer: {
      display: 'grid',
      gridTemplateColumns: 'repeat(2, 1fr)',
      // columnGap: theme.spacing(0.5) + 'px',
      // rowGap: theme.spacing(0.5) + 'px',
      gap: theme.spacing(0.5) + 'px',
      // flexDirection: 'row',
      // width: '100%',
      // flexWrap: 'wrap',
    },
    filterLabel: {
      padding: theme.spacing(0.5),
    },
    networkContainer: {
      display: 'flex',
      flexDirection: 'column',
      width: '100%',
      marginBottom: theme.spacing(1),
    },
    networkIcon: {
      width: 48,
      maxHeight: 24,
    },
    networkIconWrapper: {
      padding: '1px 5px',
      borderRadius: 8,
      width: 60,
      textAlign: 'center',
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
  readonly handleChange: (type: SelectableNetworks) => void;
  readonly showTitle?: boolean;
}

export default function NetworkSelect(props: Props) {
  const classes = useStyles();
  const {
    filters: { networks },
  } = useContext(FilterContext);
  // const selectedNetworks: SelectableNetworks = networks || [];

  const updateNetworks = (value?: NetworkType | 'all') => {
    let newSelectedNetworks: SelectableNetworks;

    if (value === 'all') {
      if (networks === 'all') {
        newSelectedNetworks = undefined;
      } else {
        newSelectedNetworks = 'all';
      }
    } else if (value) {
      // Transition from "all" or "none" to one network
      if (!networks || networks === 'all') {
        newSelectedNetworks = [value];
      } else if (!networks.includes(value)) {
        newSelectedNetworks = [...networks, value];
      } else {
        newSelectedNetworks = networks.filter(networkId => networkId !== value);
      }
    } else {
      // User selected 'None', reset genre filter
      newSelectedNetworks = undefined;
    }

    if (!selectableNetworksEqual(networks, newSelectedNetworks)) {
      props.handleChange(newSelectedNetworks);
    }
  };

  const isNetworkSelected = (networkType: NetworkType) => {
    return networks && networks.includes(networkType);
  };

  const makeNetworkChip = (
    networkType: NetworkType,
    extraStyles?: CSSProperties,
  ) => {
    const prettyName = networkToPrettyName[networkType];

    return (
      <Chip
        key={networkType}
        onClick={() => updateNetworks(networkType)}
        size="medium"
        color={isNetworkSelected(networkType) ? 'primary' : 'secondary'}
        label={prettyName}
        className={classes.chip}
        icon={
          <div
            className={classes.networkIconWrapper}
            style={{
              // padding: '3px 6px',
              // backgroundColor: networkToColor[networkType],
              // paddingTop: '9.5%',
              height: 25,
              overflow: 'hidden',
              background: `${networkToColor[networkType]} url(${getLogoUrl(
                networkType,
              )}) no-repeat 50%`,
              backgroundSize: 'contain',
              backgroundOrigin: 'content-box',
              ...extraStyles,
            }}
          >
            {/* <img
              className={clsx(classes.networkIcon)}
              src={getLogoUrl(networkType)}
              alt={`${prettyName} logo`}
            /> */}
          </div>
        }
      />
    );
  };

  return (
    <div className={classes.networkContainer}>
      {props.showTitle && <FilterSectionTitle title="Network" />}
      <div className={classes.chipContainer}>
        <Chip
          key={'all'}
          onClick={() => updateNetworks('all')}
          size="medium"
          color={networks === 'all' ? 'primary' : 'secondary'}
          label="All"
          style={{ justifyContent: 'center' }}
          className={classes.chip}
        />
        {makeNetworkChip(allNetworks.Netflix, {
          backgroundPosition: '62% 59%',
          backgroundSize: '95%',
        })}
        {makeNetworkChip(allNetworks.Hulu)}
        {makeNetworkChip(allNetworks.DisneyPlus, { backgroundSize: '100%' })}
        {makeNetworkChip('hbo-now')}
        {makeNetworkChip(allNetworks.HboMax)}
        {makeNetworkChip(allNetworks.AmazonVideo)}
        {makeNetworkChip(allNetworks.PrimeVideo)}
        {makeNetworkChip(allNetworks.AppleTv, { backgroundSize: '66%' })}
      </div>
    </div>
  );
}

NetworkSelect.defaultProps = {
  showTitle: true,
};
