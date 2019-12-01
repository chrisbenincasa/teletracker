import React, { Component } from 'react';
import {
  Button,
  ButtonGroup,
  Chip,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { NetworkType } from '../../types';
import { parseFilterParamsFromQs } from '../../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    buttonContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '75%',
      flexWrap: 'nowrap',
    },
    buttonGroup: {
      whiteSpace: 'nowrap',
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
    selectedButton: {
      backgroundColor: theme.palette.primary.main,
      color: theme.palette.common.white,
    },
    unselectedButton: {
      backgroundColor: theme.palette.grey[700],
      color: theme.palette.common.white,
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
  });

interface OwnProps {
  handleChange: (type?: NetworkType[]) => void;
  selectedNetworks?: NetworkType[];
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

export const getNetworkTypeFromUrlParam = () => {
  return parseFilterParamsFromQs(window.location.search).networks;
};

class NetworkSelect extends Component<Props> {
  componentDidUpdate = oldProps => {
    if (oldProps.location.search !== this.props.location.search) {
      // To do, only update this when these params changed
      this.setState({
        type: getNetworkTypeFromUrlParam(),
      });
    }
  };

  updateNetworks = (param: string, value?: NetworkType[]) => {
    this.props.handleChange(value);
  };

  render() {
    const { classes, selectedNetworks } = this.props;
    const isNetflixSelected =
      (selectedNetworks && selectedNetworks.includes('netflix')) ||
      (selectedNetworks && selectedNetworks.includes('netflix-kids'));
    const isHuluSelected =
      selectedNetworks && selectedNetworks.includes('hulu');
    const isHboSelected =
      (selectedNetworks && selectedNetworks.includes('hbo-go')) ||
      (selectedNetworks && selectedNetworks.includes('hbo-now'));

    return (
      <div className={classes.networkContainer}>
        <Typography className={classes.filterLabel} display="block">
          Network
        </Typography>
        <div className={classes.buttonContainer}>
          <Chip
            key={'all'}
            onClick={() => this.updateNetworks('networks', undefined)}
            size="medium"
            color={!selectedNetworks ? 'primary' : 'default'}
            label="All"
            variant="default"
            className={
              !selectedNetworks ? classes.selectedChip : classes.unselectedChip
            }
          />
          <Chip
            key={'netflix'}
            onClick={() =>
              this.updateNetworks('networks', ['netflix', 'netflix-kids'])
            }
            size="medium"
            color={isNetflixSelected ? 'primary' : 'default'}
            label="Netlflix"
            variant="default"
            className={
              isNetflixSelected ? classes.selectedChip : classes.unselectedChip
            }
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
            onClick={() => this.updateNetworks('networks', ['hulu'])}
            size="medium"
            color={isHuluSelected ? 'primary' : 'default'}
            label="Hulu"
            variant="default"
            className={
              isHuluSelected ? classes.selectedChip : classes.unselectedChip
            }
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
            onClick={() =>
              this.updateNetworks('networks', ['hbo-go', 'hbo-now'])
            }
            size="medium"
            color={isHboSelected ? 'primary' : 'default'}
            label="HBO"
            variant="default"
            className={
              isHboSelected ? classes.selectedChip : classes.unselectedChip
            }
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
}

export default withStyles(styles)(withRouter(NetworkSelect));
