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
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { NetworkType, isNetworkType } from '../../types';
import {
  parseFilterParamsFromQs,
  updateURLParameters,
} from '../../utils/urlHelper';

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
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
    networkIcon: {
      width: 20,
      borderRadius: '10%',
    },
    networkContainer: { display: 'flex', flexDirection: 'column' },
  });

interface OwnProps {
  handleChange: (type?: NetworkType[]) => void;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  type?: NetworkType[];
}

export const getNetworkTypeFromUrlParam = () => {
  return parseFilterParamsFromQs(location.search).networks;
};

class NetworkSelect extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      type: getNetworkTypeFromUrlParam(),
    };
  }

  componentDidUpdate = oldProps => {
    if (oldProps.location.search !== this.props.location.search) {
      // To do, only update this when these params changed
      this.setState({
        type: getNetworkTypeFromUrlParam(),
      });
    }
  };

  updateURLParam = (param: string, value?: NetworkType[]) => {
    updateURLParameters(this.props, param, value);

    this.setState(
      {
        type: value,
      },
      () => {
        this.props.handleChange(value);
      },
    );
  };

  render() {
    const { classes } = this.props;
    const { type } = this.state;

    return (
      <div className={classes.networkContainer}>
        <Typography display="block">By Network:</Typography>
        <div className={classes.buttonContainer}>
          <ButtonGroup
            variant="contained"
            color="primary"
            aria-label="Filter by Netflix, Hulu, HBO, or All"
            className={classes.buttonGroup}
          >
            <Button
              color={!type ? 'secondary' : 'primary'}
              onClick={() => this.updateURLParam('networks', undefined)}
              className={classes.filterButtons}
            >
              All
            </Button>
            <Button
              color={
                (type && type.includes('netflix')) ||
                (type && type.includes('netflix-kids'))
                  ? 'secondary'
                  : 'primary'
              }
              onClick={() =>
                this.updateURLParam('networks', ['netflix', 'netflix-kids'])
              }
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/netflix/icon.jpg`}
                />
              }
              className={classes.filterButtons}
            >
              Netflix
            </Button>
            <Button
              color={type && type.includes('hulu') ? 'secondary' : 'primary'}
              onClick={() => this.updateURLParam('networks', ['hulu'])}
              className={classes.filterButtons}
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/hulu/icon.jpg`}
                />
              }
            >
              Hulu
            </Button>
            <Button
              color={
                (type && type.includes('hbo-go')) ||
                (type && type.includes('hbo-now'))
                  ? 'secondary'
                  : 'primary'
              }
              onClick={() =>
                this.updateURLParam('networks', ['hbo-go', 'hbo-now'])
              }
              className={classes.filterButtons}
              startIcon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/hbo-now/icon.jpg`}
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
