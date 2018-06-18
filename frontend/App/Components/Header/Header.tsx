import * as React from 'react';

import styles from '../../Themes/ApplicationStyles';
import { Header as NativeHeader } from 'react-native-elements';
import HeaderCenter from './HeaderCenter';
import HeaderLeft from './HeaderLeft';
import HeaderRight from './HeaderRight';

interface Props {
    title?: string
}

export default class Header extends React.PureComponent<Props> {
    render() {
        return (
            <NativeHeader
                outerContainerStyles={styles.header.outer}
                innerContainerStyles={styles.header.inner}
                statusBarProps={styles.header.statusBarProps}
            >
                {
                    this.props.children ? (
                        this.props.children
                    ) : (
                        <HeaderWrapper>
                            <HeaderLeft {...this.props} />
                            <HeaderCenter {...this.props} />
                            <HeaderRight {...this.props} />
                        </HeaderWrapper>
                    )
                }
                
            </NativeHeader> 
        );
    }
}

class HeaderWrapper extends React.PureComponent {
    render() {
        return (this.props.children);
    }
}