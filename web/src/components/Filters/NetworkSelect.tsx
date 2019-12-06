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
    chip: {
      margin: theme.spacing(0.25),
      flexGrow: 1,
    },
    chipContainer: {
      display: 'flex',
      flexDirection: 'row',
      width: '75%',
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
        <div className={classes.chipContainer}>
          <Chip
            key={'all'}
            onClick={() => this.updateNetworks('networks', undefined)}
            size="medium"
            color={!selectedNetworks ? 'primary' : 'secondary'}
            label="All"
            className={classes.chip}
          />
          <Chip
            key={'netflix'}
            onClick={() =>
              this.updateNetworks('networks', ['netflix', 'netflix-kids'])
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
            onClick={() => this.updateNetworks('networks', ['hulu'])}
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
            onClick={() =>
              this.updateNetworks('networks', ['hbo-go', 'hbo-now'])
            }
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
}

export default withStyles(styles)(withRouter(NetworkSelect));
