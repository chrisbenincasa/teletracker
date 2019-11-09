import {
  CardMedia,
  createStyles,
  Fade,
  Theme,
  Typography,
  WithStyles,
  withStyles,
} from '@material-ui/core';
import React, { Component } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import imagePlaceholder from '../assets/images/imagePlaceholder.png';
import ManageTracking from '../components/ManageTracking';
import { ResponsiveImage } from '../components/ResponsiveImage';
import { ApiItem } from '../types/v2';
import { Item } from '../types/v2/Item';
import AddToListDialog from './AddToListDialog';
import withUser, { WithUserProps } from './withUser';

const styles = (theme: Theme) =>
  createStyles({
    backdropContainer: {
      height: 'auto',
      overflow: 'hidden',
      top: 0,
      width: '100%',
    },
    backdropGradient: {
      position: 'absolute',
      top: 0,
      width: '100%',
      height: '100%',
      backgroundImage:
        'linear-gradient(to bottom, rgba(255, 255, 255,0) 0%,rgba(48, 48, 48,1) 100%)',
    },
    posterContainer: {
      display: 'flex',
      flex: '0 1 auto',
      flexDirection: 'column',
      position: 'absolute',
      height: 'auto',
      top: theme.spacing(3),
      left: theme.spacing(3),
      width: '25%',
      [theme.breakpoints.up('md')]: {
        width: 225,
      },
    },
    title: {
      [theme.breakpoints.up('sm')]: {
        fontSize: '2.5em',
      },
      textAlign: 'right',
      width: '70%',
      alignSelf: 'flex-end',
      fontSize: '1.5em',
      fontWeight: 700,
    },
    titleContainer: {
      display: 'flex',
      flexDirection: 'column',
      alignItems: 'flex-start',
      position: 'absolute',
      bottom: 0,
      right: theme.spacing(3),
      marginBottom: theme.spacing(3),
      width: '65%',
    },
  });

interface OwnProps {
  featuredItem: Item;
}

type Props = OwnProps & WithStyles<typeof styles> & WithUserProps;

interface State {
  manageTrackingModalOpen: boolean;
  imageLoading: boolean;
}

class Featured extends Component<Props, State> {
  state: State = {
    manageTrackingModalOpen: false,
    imageLoading: true,
  };

  componentDidUpdate(
    prevProps: Readonly<Props>,
    prevState: Readonly<State>,
  ): void {
    if (
      (!prevProps.featuredItem && this.props.featuredItem) ||
      (this.props.featuredItem &&
        prevProps.featuredItem.id !== this.props.featuredItem.id)
    ) {
      this.setState({
        imageLoading: true,
      });
    }
  }

  openManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: true });
  };

  closeManageTrackingModal = () => {
    this.setState({ manageTrackingModalOpen: false });
  };

  renderTitle = (item: ApiItem) => {
    const { classes } = this.props;
    const title = item.original_title || '';

    return (
      <div className={classes.titleContainer}>
        <Typography color="inherit" variant="h3" className={classes.title}>
          {`${title}`}
        </Typography>
      </div>
    );
  };

  imageLoaded = () => {
    this.setState({
      imageLoading: false,
    });
  };

  renderFeaturedItem = () => {
    let { classes, featuredItem, userSelf } = this.props;
    const { manageTrackingModalOpen } = this.state;

    return featuredItem ? (
      <Fade in={!this.state.imageLoading}>
        <div style={{ position: 'relative' }}>
          <div className={classes.backdropContainer}>
            <ResponsiveImage
              item={featuredItem}
              imageType="backdrop"
              imageStyle={{
                objectFit: 'cover',
                width: '100%',
                height: '100%',
                maxHeight: 424,
              }}
              pictureStyle={{
                display: 'block',
              }}
              loadCallback={this.imageLoaded}
            />
            <div className={classes.backdropGradient} />
          </div>

          <div className={classes.posterContainer}>
            <RouterLink
              to={'/' + featuredItem.type + '/' + featuredItem.slug}
              style={{
                display: 'block',
                height: '100%',
                textDecoration: 'none',
              }}
            >
              <CardMedia
                src={imagePlaceholder}
                item={featuredItem}
                component={ResponsiveImage}
                imageType="poster"
                imageStyle={{
                  boxShadow: '7px 10px 23px -8px rgba(0,0,0,0.57)',
                  maxWidth: '100%',
                  maxHeight: '100%',
                }}
                pictureStyle={{
                  height: '100%',
                  width: '100%',
                  display: 'block',
                }}
              />
            </RouterLink>

            <ManageTracking
              itemDetail={featuredItem}
              style={{ maxWidth: 225 }}
            />
            <AddToListDialog
              open={manageTrackingModalOpen}
              onClose={this.closeManageTrackingModal.bind(this)}
              userSelf={userSelf!}
              item={featuredItem}
            />
          </div>
          {this.renderTitle(featuredItem)}
        </div>
      </Fade>
    ) : null;
  };

  render() {
    return this.renderFeaturedItem();
  }
}

export default withUser(withStyles(styles)(Featured));
