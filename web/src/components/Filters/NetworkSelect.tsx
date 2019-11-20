import {
  Button,
  ButtonGroup,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { NetworkType } from '../../types';
import { parseFilterParamsFromQs } from '../../utils/urlHelper';

const styles = (theme: Theme) =>
  createStyles({
    buttonContainer: {
      display: 'flex',
      flexDirection: 'row',
      flexWrap: 'wrap',
    },
    buttonGroup: {
      marginRight: theme.spacing(1),
      whiteSpace: 'nowrap',
    },
    filterLabel: {
      paddingBottom: theme.spacing() / 2,
    },
    networkIcon: {
      width: 20,
      borderRadius: '10%',
    },
    networkContainer: { display: 'flex', flexDirection: 'column' },
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

    return (
      <div className={classes.networkContainer}>
        <Typography className={classes.filterLabel} display="block">
          Network
        </Typography>
        <div className={classes.buttonContainer}>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by Netflix, Hulu, HBO, or All"
            className={classes.buttonGroup}
          >
            <Button
              color={!selectedNetworks ? 'secondary' : 'primary'}
              onClick={() => this.updateNetworks('networks', undefined)}
            >
              All
            </Button>
            <Button
              color={
                (selectedNetworks && selectedNetworks.includes('netflix')) ||
                (selectedNetworks && selectedNetworks.includes('netflix-kids'))
                  ? 'secondary'
                  : 'primary'
              }
              onClick={() =>
                this.updateNetworks('networks', ['netflix', 'netflix-kids'])
              }
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/netflix/icon.jpg`}
                  alt="Netflix logo"
                />
              }
            >
              Netflix
            </Button>
            <Button
              color={
                selectedNetworks && selectedNetworks.includes('hulu')
                  ? 'secondary'
                  : 'primary'
              }
              onClick={() => this.updateNetworks('networks', ['hulu'])}
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/hulu/icon.jpg`}
                  alt="Hulu logo"
                />
              }
            >
              Hulu
            </Button>
            <Button
              color={
                (selectedNetworks && selectedNetworks.includes('hbo-go')) ||
                (selectedNetworks && selectedNetworks.includes('hbo-now'))
                  ? 'secondary'
                  : 'primary'
              }
              onClick={() =>
                this.updateNetworks('networks', ['hbo-go', 'hbo-now'])
              }
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/hbo-now/icon.jpg`}
                  alt="HBO logo"
                />
              }
            >
              HBO
            </Button>
          </ButtonGroup>
        </div>
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(NetworkSelect));
