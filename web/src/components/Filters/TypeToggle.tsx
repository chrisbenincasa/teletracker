import {
  Button,
  ButtonGroup,
  createStyles,
  Theme,
  Typography,
  withStyles,
  WithStyles,
} from '@material-ui/core';
import {
  Link as RouterLink,
  withRouter,
  RouteComponentProps,
} from 'react-router-dom';
import { ItemTypes } from '../../types';
import React, { Component } from 'react';

const styles = (theme: Theme) =>
  createStyles({
    filterButtons: {
      [theme.breakpoints.down('sm')]: {
        fontSize: '0.575rem',
      },
    },
  });

interface OwnProps {
  handleChange: (type: ItemTypes) => void;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

interface State {
  type?: ItemTypes;
}

export const getTypeFromUrlParam = () => {
  let params = new URLSearchParams(location.search);
  let type;
  let param = params.get('type');
  if (param === 'movie' || param === 'show') {
    type = [param];
  }
  return type;
};

class TypeToggle extends Component<Props, State> {
  constructor(props: Props) {
    super(props);

    this.state = {
      ...this.state,
      type: getTypeFromUrlParam(),
    };
  }

  componentDidUpdate(prevProps: Props) {
    if (
      this.props.location &&
      prevProps.location.search !== this.props.location.search
    ) {
      const type = getTypeFromUrlParam();
      this.setState(
        {
          type,
        },
        () => {
          this.props.handleChange(type);
        },
      );
    }
  }

  render() {
    const { classes } = this.props;
    const { type } = this.state;

    return (
      <ButtonGroup
        variant="contained"
        color="primary"
        aria-label="Filter by All, Movies, or just TV Shows"
      >
        <Button
          color={!type ? 'secondary' : 'primary'}
          component={RouterLink}
          to={{
            pathname: location.pathname,
            search: '',
          }}
          className={classes.filterButtons}
        >
          All
        </Button>
        <Button
          color={type && type.includes('movie') ? 'secondary' : 'primary'}
          component={RouterLink}
          to={'?type=movie'}
          className={classes.filterButtons}
        >
          Movies
        </Button>
        <Button
          color={type && type.includes('show') ? 'secondary' : 'primary'}
          component={RouterLink}
          to={'?type=show'}
          className={classes.filterButtons}
        >
          TV
        </Button>
      </ButtonGroup>
    );
  }
}

export default withStyles(styles)(withRouter(TypeToggle));
